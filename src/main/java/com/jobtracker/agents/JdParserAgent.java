package com.jobtracker.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.client.OpenRouterClient;
import com.jobtracker.exception.AgentException;
import org.springframework.stereotype.Component;

@Component
public class JdParserAgent {

    public static final String AGENT_NAME = "JdParserAgent";

    private static final String SYSTEM_PROMPT = """
            You are a job description parser. Given a job description, extract:
            - skills: array of technical skills mentioned (e.g. Java, Spring Boot, PostgreSQL)
            - seniority: one of Junior, Mid, Senior, Staff, Principal
            - domain: one of Backend, Frontend, Fullstack, Mobile, Data, ML, DevOps, Security, Other

            Respond with ONLY valid JSON in this exact shape, no prose, no markdown fences:
            {"skills": ["..."], "seniority": "...", "domain": "..."}
            """;

    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;

    public JdParserAgent(OpenRouterClient openRouterClient, ObjectMapper objectMapper) {
        this.openRouterClient = openRouterClient;
        this.objectMapper = objectMapper;
    }

    public AgentInvocation<JdParseResult> parse(String jdText) {
        if (jdText == null || jdText.isBlank()) {
            throw new AgentException("Cannot parse empty job description");
        }
        String rawOutput = openRouterClient.complete(SYSTEM_PROMPT, jdText);
        JdParseResult result = AgentJsonExtractor.parse(rawOutput, JdParseResult.class, objectMapper);
        return new AgentInvocation<>(jdText, rawOutput, result);
    }
}
