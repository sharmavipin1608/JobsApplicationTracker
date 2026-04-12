package com.jobtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.agents.AgentInvocation;
import com.jobtracker.agents.JdParseResult;
import com.jobtracker.agents.JdParserAgent;
import com.jobtracker.agents.ResumeScorerAgent;
import com.jobtracker.agents.ScoreResult;
import com.jobtracker.client.OpenRouterClient;
import com.jobtracker.enums.AgentRunStatus;
import com.jobtracker.model.AgentRun;
import com.jobtracker.model.Job;
import com.jobtracker.model.Score;
import com.jobtracker.repository.AgentRunRepository;
import com.jobtracker.repository.JobRepository;
import com.jobtracker.repository.ScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final JdParserAgent jdParserAgent;
    private final ResumeScorerAgent resumeScorerAgent;
    private final OpenRouterClient openRouterClient;
    private final AgentRunRepository agentRunRepository;
    private final ScoreRepository scoreRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public OrchestratorService(JdParserAgent jdParserAgent,
                               ResumeScorerAgent resumeScorerAgent,
                               OpenRouterClient openRouterClient,
                               AgentRunRepository agentRunRepository,
                               ScoreRepository scoreRepository,
                               JobRepository jobRepository,
                               ObjectMapper objectMapper) {
        this.jdParserAgent = jdParserAgent;
        this.resumeScorerAgent = resumeScorerAgent;
        this.openRouterClient = openRouterClient;
        this.agentRunRepository = agentRunRepository;
        this.scoreRepository = scoreRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    @Async("agentExecutor")
    public void analyze(UUID jobId, String jdText) {
        log.info("Starting analysis pipeline for job {}", jobId);

        // Step 1: Parse JD
        JdParseResult parsedJd;
        try {
            AgentInvocation<JdParseResult> parseInvocation = jdParserAgent.parse(jdText);
            parsedJd = parseInvocation.result();
            saveAgentRun(jobId, JdParserAgent.AGENT_NAME, parseInvocation.inputText(),
                    parseInvocation.rawOutput(), AgentRunStatus.SUCCESS);
            log.info("JdParserAgent succeeded for job {}", jobId);
        } catch (Exception ex) {
            log.error("JdParserAgent failed for job {}", jobId, ex);
            saveAgentRun(jobId, JdParserAgent.AGENT_NAME, jdText, ex.getMessage(), AgentRunStatus.FAILED);
            return;
        }

        // Step 2: Score resume fit
        try {
            AgentInvocation<ScoreResult> scoreInvocation = resumeScorerAgent.score(parsedJd);
            ScoreResult scoreResult = scoreInvocation.result();
            saveAgentRun(jobId, ResumeScorerAgent.AGENT_NAME, scoreInvocation.inputText(),
                    scoreInvocation.rawOutput(), AgentRunStatus.SUCCESS);
            log.info("ResumeScorerAgent succeeded for job {} — fit_score={}", jobId, scoreResult.fitScore());

            // Persist score
            saveScore(jobId, scoreResult);

            // Update denormalized fit_score on jobs table
            updateJobFitScore(jobId, scoreResult.fitScore());
        } catch (Exception ex) {
            log.error("ResumeScorerAgent failed for job {}", jobId, ex);
            saveAgentRun(jobId, ResumeScorerAgent.AGENT_NAME, buildFallbackInput(parsedJd),
                    ex.getMessage(), AgentRunStatus.FAILED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAgentRun(UUID jobId, String agentName, String inputText,
                              String outputText, AgentRunStatus status) {
        AgentRun run = new AgentRun();
        run.setJobId(jobId);
        run.setAgentName(agentName);
        run.setModelUsed(openRouterClient.getModel());
        run.setInputText(inputText);
        run.setOutputText(outputText);
        run.setStatus(status);
        agentRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveScore(UUID jobId, ScoreResult scoreResult) {
        Score score = new Score();
        score.setJobId(jobId);
        score.setFitScore(scoreResult.fitScore());
        try {
            score.setRecommendations(objectMapper.writeValueAsString(scoreResult.recommendations()));
        } catch (JsonProcessingException ex) {
            score.setRecommendations(scoreResult.recommendations().toString());
        }
        scoreRepository.save(score);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobFitScore(UUID jobId, int fitScore) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setFitScore(fitScore);
            jobRepository.save(job);
        });
    }

    private String buildFallbackInput(JdParseResult parsedJd) {
        try {
            return objectMapper.writeValueAsString(parsedJd);
        } catch (JsonProcessingException ex) {
            return parsedJd.toString();
        }
    }
}
