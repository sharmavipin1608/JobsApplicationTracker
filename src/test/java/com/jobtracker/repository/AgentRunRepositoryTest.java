package com.jobtracker.repository;

import com.jobtracker.enums.AgentRunStatus;
import com.jobtracker.enums.JobStatus;
import com.jobtracker.model.AgentRun;
import com.jobtracker.model.Job;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AgentRunRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private AgentRunRepository agentRunRepository;
    @Autowired private JobRepository jobRepository;

    private Job persistJob() {
        Job job = new Job();
        job.setCompany("Acme");
        job.setRole("Engineer");
        job.setStatus(JobStatus.UNDETERMINED);
        return jobRepository.save(job);
    }

    @Test
    void shouldSaveAgentRun_withDefaultPendingStatus() {
        Job job = persistJob();
        AgentRun run = new AgentRun();
        run.setJobId(job.getId());
        run.setAgentName("JdParserAgent");
        run.setInputText("some jd");

        AgentRun saved = agentRunRepository.save(run);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(AgentRunStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindAgentRunsByJobId_inChronologicalOrder() {
        Job job = persistJob();

        AgentRun first = new AgentRun();
        first.setJobId(job.getId());
        first.setAgentName("JdParserAgent");
        first.setStatus(AgentRunStatus.SUCCESS);
        agentRunRepository.saveAndFlush(first);

        AgentRun second = new AgentRun();
        second.setJobId(job.getId());
        second.setAgentName("ResumeScorerAgent");
        second.setStatus(AgentRunStatus.SUCCESS);
        agentRunRepository.saveAndFlush(second);

        List<AgentRun> runs = agentRunRepository.findByJobIdOrderByCreatedAtAsc(job.getId());

        assertThat(runs).hasSize(2);
        assertThat(runs).extracting(AgentRun::getAgentName)
                .containsExactly("JdParserAgent", "ResumeScorerAgent");
    }
}
