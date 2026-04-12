package com.jobtracker.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens
) {
    public record Message(String role, String content) {}
}
