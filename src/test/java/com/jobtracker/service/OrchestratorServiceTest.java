package com.jobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.agents.AgentInvocation;
import com.jobtracker.agents.JdParseResult;
import com.jobtracker.agents.JdParserAgent;
import com.jobtracker.agents.ResumeScorerAgent;
import com.jobtracker.agents.ScoreResult;
import com.jobtracker.client.OpenRouterClient;
import com.jobtracker.enums.AgentRunStatus;
import com.jobtracker.exception.AgentException;
import com.jobtracker.model.AgentRun;
import com.jobtracker.model.Job;
import com.jobtracker.model.Score;
import com.jobtracker.repository.AgentRunRepository;
import com.jobtracker.repository.JobRepository;
import com.jobtracker.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock private JdParserAgent jdParserAgent;
    @Mock private ResumeScorerAgent resumeScorerAgent;
    @Mock private OpenRouterClient openRouterClient;
    @Mock private AgentRunRepository agentRunRepository;
    @Mock private ScoreRepository scoreRepository;
    @Mock private JobRepository jobRepository;

    private OrchestratorService orchestrator;

    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orchestrator = new OrchestratorService(
                jdParserAgent, resumeScorerAgent, openRouterClient,
                agentRunRepository, scoreRepository, jobRepository,
                new ObjectMapper()
        );
        lenient().when(openRouterClient.getModel()).thenReturn("test-model");
    }

    @Test
    void shouldRunFullPipeline_whenBothAgentsSucceed() {
        JdParseResult parseResult = new JdParseResult(List.of("Java"), "Senior", "Backend");
        ScoreResult scoreResult = new ScoreResult(82, List.of("Add GCP", "Add Kafka", "Highlight payments"));

        when(jdParserAgent.parse("some jd"))
                .thenReturn(new AgentInvocation<>("some jd", "{raw}", parseResult));
        when(resumeScorerAgent.score(parseResult))
                .thenReturn(new AgentInvocation<>("prompt", "{raw}", scoreResult));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(new Job()));

        orchestrator.analyze(jobId, "some jd");

        // Two agent_runs saved (both SUCCESS)
        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository, times(2)).save(runCaptor.capture());
        List<AgentRun> runs = runCaptor.getAllValues();
        assertThat(runs.get(0).getAgentName()).isEqualTo("JdParserAgent");
        assertThat(runs.get(0).getStatus()).isEqualTo(AgentRunStatus.SUCCESS);
        assertThat(runs.get(1).getAgentName()).isEqualTo("ResumeScorerAgent");
        assertThat(runs.get(1).getStatus()).isEqualTo(AgentRunStatus.SUCCESS);

        // Score saved
        ArgumentCaptor<Score> scoreCaptor = ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository).save(scoreCaptor.capture());
        assertThat(scoreCaptor.getValue().getFitScore()).isEqualTo(82);

        // Job fit_score updated
        verify(jobRepository).findById(jobId);
        verify(jobRepository).save(any(Job.class));
    }

    @Test
    void shouldLogFailedRun_whenJdParserFails() {
        when(jdParserAgent.parse("bad jd"))
                .thenThrow(new AgentException("parse error"));

        orchestrator.analyze(jobId, "bad jd");

        // One agent_run saved with FAILED
        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getAgentName()).isEqualTo("JdParserAgent");
        assertThat(runCaptor.getValue().getStatus()).isEqualTo(AgentRunStatus.FAILED);

        // Scorer never called
        verifyNoInteractions(resumeScorerAgent);
        verifyNoInteractions(scoreRepository);
    }

    @Test
    void shouldLogFailedRun_whenResumeScorerFails() {
        JdParseResult parseResult = new JdParseResult(List.of("Java"), "Senior", "Backend");
        when(jdParserAgent.parse("jd"))
                .thenReturn(new AgentInvocation<>("jd", "{raw}", parseResult));
        when(resumeScorerAgent.score(parseResult))
                .thenThrow(new AgentException("score error"));

        orchestrator.analyze(jobId, "jd");

        // Two agent_runs: first SUCCESS, second FAILED
        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository, times(2)).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().get(0).getStatus()).isEqualTo(AgentRunStatus.SUCCESS);
        assertThat(runCaptor.getAllValues().get(1).getStatus()).isEqualTo(AgentRunStatus.FAILED);

        // No score saved, job not updated
        verifyNoInteractions(scoreRepository);
    }

    @Test
    void shouldRecordModelUsed_inAgentRuns() {
        when(openRouterClient.getModel()).thenReturn("meta-llama/llama-3.1-8b-instruct:free");
        JdParseResult parseResult = new JdParseResult(List.of("Java"), "Senior", "Backend");
        when(jdParserAgent.parse("jd"))
                .thenReturn(new AgentInvocation<>("jd", "{raw}", parseResult));
        when(resumeScorerAgent.score(parseResult))
                .thenReturn(new AgentInvocation<>("prompt", "{raw}", new ScoreResult(50, List.of("a", "b", "c"))));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(new Job()));

        orchestrator.analyze(jobId, "jd");

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository, times(2)).save(runCaptor.capture());
        runCaptor.getAllValues().forEach(run ->
                assertThat(run.getModelUsed()).isEqualTo("meta-llama/llama-3.1-8b-instruct:free")
        );
    }
}
