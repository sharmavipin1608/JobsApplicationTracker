package com.jobtracker.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.exception.AgentException;

/**
 * Defensive JSON parsing for LLM output.
 * Handles common cases:
 * - Clean JSON: {"foo": "bar"}
 * - Markdown-fenced: ```json {"foo":"bar"} ```
 * - JSON with surrounding prose: "Sure! Here is the JSON: {"foo":"bar"}"
 */
final class AgentJsonExtractor {

    private AgentJsonExtractor() {}

    static <T> T parse(String rawModelOutput, Class<T> targetType, ObjectMapper mapper) {
        if (rawModelOutput == null || rawModelOutput.isBlank()) {
            throw new AgentException("Model returned empty output");
        }
        String json = extractJson(rawModelOutput);
        try {
            return mapper.readValue(json, targetType);
        } catch (JsonProcessingException ex) {
            throw new AgentException(
                    "Failed to parse model output as " + targetType.getSimpleName() + ": " + json,
                    ex
            );
        }
    }

    private static String extractJson(String raw) {
        String s = raw.trim();
        // Strip markdown code fences (```json ... ``` or ``` ... ```)
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        // Find the first { and last } to slice out the JSON object
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }
}
