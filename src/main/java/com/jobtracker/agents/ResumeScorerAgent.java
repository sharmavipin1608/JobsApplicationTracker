package com.jobtracker.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.client.OpenRouterClient;
import com.jobtracker.exception.AgentException;
import org.springframework.stereotype.Component;

@Component
public class ResumeScorerAgent {

    public static final String AGENT_NAME = "ResumeScorerAgent";

    // Hardcoded resume summary for V2. Will be replaced by file/Drive upload in V3.
    private static final String RESUME_SUMMARY = """
            Senior Software Engineer with 8 years of experience.
            Strong in: Java, Spring Boot, PostgreSQL, REST APIs, microservices, Kubernetes.
            Some experience with: Python, AWS, Kafka.
            Domain: backend systems, distributed systems, payments.
            """;

    private static final String SYSTEM_PROMPT = """
            You are a resume scorer. Given a parsed job description (skills, seniority, domain) \
            and a candidate resume summary, score how well the resume fits the role from 0 to 100 \
            and provide exactly 3 specific, actionable recommendations to improve the resume for THIS role.

            Respond with ONLY valid JSON in this exact shape, no prose, no markdown fences:
            {"fit_score": <integer 0-100>, "recommendations": ["...", "...", "..."]}
            """;

    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;

    public ResumeScorerAgent(OpenRouterClient openRouterClient, ObjectMapper objectMapper) {
        this.openRouterClient = openRouterClient;
        this.objectMapper = objectMapper;
    }

    public AgentInvocation<ScoreResult> score(JdParseResult parsedJd) {
        if (parsedJd == null) {
            throw new AgentException("Cannot score against a null parsed JD");
        }
        String userPrompt = buildUserPrompt(parsedJd);
        String rawOutput = openRouterClient.complete(SYSTEM_PROMPT, userPrompt);
        ScoreResult result = AgentJsonExtractor.parse(rawOutput, ScoreResult.class, objectMapper);
        if (result.fitScore() < 0 || result.fitScore() > 100) {
            throw new AgentException("Model returned out-of-range fit_score: " + result.fitScore());
        }
        return new AgentInvocation<>(userPrompt, rawOutput, result);
    }

    private String buildUserPrompt(JdParseResult parsedJd) {
        try {
            String jdJson = objectMapper.writeValueAsString(parsedJd);
            return "Parsed JD:\n" + jdJson + "\n\nCandidate resume:\n" + RESUME_SUMMARY;
        } catch (JsonProcessingException ex) {
            throw new AgentException("Failed to serialize parsed JD for scoring prompt", ex);
        }
    }
}
