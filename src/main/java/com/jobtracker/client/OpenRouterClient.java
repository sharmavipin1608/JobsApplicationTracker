package com.jobtracker.client;

import com.jobtracker.client.dto.ChatCompletionRequest;
import com.jobtracker.client.dto.ChatCompletionResponse;
import com.jobtracker.config.OpenRouterProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class OpenRouterClient {

    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_TOKENS = 500;

    private final RestClient restClient;
    private final OpenRouterProperties props;

    public OpenRouterClient(RestClient.Builder builder, OpenRouterProperties props) {
        this.props = props;
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("HTTP-Referer", props.siteUrl())
                .defaultHeader("X-Title", "Job Application Tracker")
                .build();
    }

    /**
     * Sends a system + user prompt to OpenRouter and returns the assistant's text reply.
     * @throws OpenRouterException on HTTP failure or empty response.
     */
    public String complete(String systemPrompt, String userPrompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                props.model(),
                List.of(
                        new ChatCompletionRequest.Message("system", systemPrompt),
                        new ChatCompletionRequest.Message("user", userPrompt)
                ),
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS
        );

        ChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientResponseException ex) {
            throw new OpenRouterException(
                    "OpenRouter call failed with status " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(),
                    ex
            );
        } catch (Exception ex) {
            throw new OpenRouterException("OpenRouter call failed: " + ex.getMessage(), ex);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new OpenRouterException("OpenRouter returned empty response");
        }
        return response.choices().get(0).message().content();
    }

    public String getModel() {
        return props.model();
    }
}
