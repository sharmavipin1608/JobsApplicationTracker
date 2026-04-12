package com.jobtracker.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jobtracker.enums.AgentRunStatus;
import com.jobtracker.model.AgentRun;
import com.jobtracker.model.Job;
import com.jobtracker.model.Score;
import com.jobtracker.repository.AgentRunRepository;
import com.jobtracker.repository.JobRepository;
import com.jobtracker.repository.ScoreRepository;
import com.jobtracker.enums.JobStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class JobAnalyzeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(0);
        wireMock.start();
    }

    @BeforeAll
    static void configureWireMock() {
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("openrouter.api-key", () -> "test-api-key");
        registry.add("openrouter.base-url", () -> "http://localhost:" + wireMock.port());
        registry.add("openrouter.model", () -> "test-model");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JobRepository jobRepository;
    @Autowired private AgentRunRepository agentRunRepository;
    @Autowired private ScoreRepository scoreRepository;

    private Job createJobWithJd(String jdText) {
        Job job = new Job();
        job.setCompany("Acme Corp");
        job.setRole("Senior Engineer");
        job.setJdText(jdText);
        job.setStatus(JobStatus.UNDETERMINED);
        return jobRepository.save(job);
    }

    @Test
    void shouldRunFullPipeline_andPersistResults() throws Exception {
        // Stub JdParserAgent call (system prompt contains "job description parser")
        stubFor(WireMock.post(urlEqualTo("/chat/completions"))
                .withRequestBody(WireMock.containing("job description parser"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "choices": [{
                                    "message": {
                                      "role": "assistant",
                                      "content": "{\\"skills\\": [\\"Java\\", \\"Spring Boot\\"], \\"seniority\\": \\"Senior\\", \\"domain\\": \\"Backend\\"}"
                                    }
                                  }]
                                }
                                """)));

        // Stub ResumeScorerAgent call (system prompt contains "resume scorer")
        stubFor(WireMock.post(urlEqualTo("/chat/completions"))
                .withRequestBody(WireMock.containing("resume scorer"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "choices": [{
                                    "message": {
                                      "role": "assistant",
                                      "content": "{\\"fit_score\\": 85, \\"recommendations\\": [\\"Add GCP\\", \\"Highlight Kafka\\", \\"Mention payments\\"]}"
                                    }
                                  }]
                                }
                                """)));

        Job job = createJobWithJd("We need a senior Java engineer with Spring Boot and PostgreSQL.");

        // Trigger analysis
        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/analyze"))
                .andExpect(status().isAccepted());

        // Wait for async pipeline to complete (agent_runs should have 2 SUCCESS rows)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AgentRun> runs = agentRunRepository.findByJobIdOrderByCreatedAtAsc(job.getId());
            assertThat(runs).hasSize(2);
            assertThat(runs).allMatch(r -> r.getStatus() == AgentRunStatus.SUCCESS);
        });

        // Verify agent_runs
        List<AgentRun> runs = agentRunRepository.findByJobIdOrderByCreatedAtAsc(job.getId());
        assertThat(runs.get(0).getAgentName()).isEqualTo("JdParserAgent");
        assertThat(runs.get(0).getModelUsed()).isEqualTo("test-model");
        assertThat(runs.get(1).getAgentName()).isEqualTo("ResumeScorerAgent");

        // Verify score persisted
        Optional<Score> score = scoreRepository.findFirstByJobIdOrderByCreatedAtDesc(job.getId());
        assertThat(score).isPresent();
        assertThat(score.get().getFitScore()).isEqualTo(85);
        assertThat(score.get().getRecommendations()).contains("Add GCP");

        // Verify jobs.fit_score updated
        Job updated = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(updated.getFitScore()).isEqualTo(85);

        // Verify GET /score endpoint returns the result
        mockMvc.perform(get("/api/v1/jobs/" + job.getId() + "/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitScore").value(85))
                .andExpect(jsonPath("$.recommendations[0]").value("Add GCP"));
    }

    @Test
    void shouldReturn400_whenJobHasNoJdText() throws Exception {
        Job job = new Job();
        job.setCompany("Acme");
        job.setRole("Engineer");
        job.setStatus(JobStatus.UNDETERMINED);
        job = jobRepository.save(job);

        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/analyze"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_JD_TEXT"));
    }

    @Test
    void shouldReturn404_whenJobDoesNotExist_onAnalyze() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/" + UUID.randomUUID() + "/analyze"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldLogFailedRun_whenOpenRouterReturnsError() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\":\"internal server error\"}")));

        Job job = createJobWithJd("Some JD text.");

        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/analyze"))
                .andExpect(status().isAccepted());

        // Wait for async to finish with FAILED status
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AgentRun> runs = agentRunRepository.findByJobIdOrderByCreatedAtAsc(job.getId());
            assertThat(runs).hasSizeGreaterThanOrEqualTo(1);
            assertThat(runs.get(0).getStatus()).isEqualTo(AgentRunStatus.FAILED);
        });

        // No score should have been saved
        assertThat(scoreRepository.findFirstByJobIdOrderByCreatedAtDesc(job.getId())).isEmpty();
    }
}
