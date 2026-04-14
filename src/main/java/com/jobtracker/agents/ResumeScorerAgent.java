package com.jobtracker.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.client.OpenRouterClient;
import com.jobtracker.exception.AgentException;
import com.jobtracker.service.ResumeService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ResumeScorerAgent {

    public static final String AGENT_NAME = "ResumeScorerAgent";

    private static final String SYSTEM_PROMPT = """
            You are a resume scorer. Given a parsed job description (skills, seniority, domain) \
            and a candidate resume summary, score how well the resume fits the role from 0 to 100 \
            and provide exactly 3 specific, actionable recommendations to improve the resume for THIS role.

            Respond with ONLY valid JSON in this exact shape, no prose, no markdown fences:
            {"fit_score": <integer 0-100>, "recommendations": ["...", "...", "..."]}
            """;

    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    private final ResumeService resumeService;

    public ResumeScorerAgent(OpenRouterClient openRouterClient, ObjectMapper objectMapper,
                              ResumeService resumeService) {
        this.openRouterClient = openRouterClient;
        this.objectMapper = objectMapper;
        this.resumeService = resumeService;
    }

    public AgentInvocation<ScoreResult> score(UUID jobId, JdParseResult parsedJd) {
        if (parsedJd == null) {
            throw new AgentException("Cannot score against a null parsed JD");
        }
        String userPrompt = buildUserPrompt(jobId, parsedJd);
        String rawOutput = openRouterClient.complete(SYSTEM_PROMPT, userPrompt);
        ScoreResult result = AgentJsonExtractor.parse(rawOutput, ScoreResult.class, objectMapper);
        if (result.fitScore() < 0 || result.fitScore() > 100) {
            throw new AgentException("Model returned out-of-range fit_score: " + result.fitScore());
        }
        return new AgentInvocation<>(userPrompt, rawOutput, result);
    }

    private String buildUserPrompt(UUID jobId, JdParseResult parsedJd) {
        try {
            String jdJson = objectMapper.writeValueAsString(parsedJd);
            String resumeText = resumeService.getResumeTextForScoring(jobId);
            return "Parsed JD:\n" + jdJson + "\n\nCandidate resume:\n" + resumeText;
        } catch (JsonProcessingException ex) {
            throw new AgentException("Failed to serialize parsed JD for scoring prompt", ex);
        }
    }
}
