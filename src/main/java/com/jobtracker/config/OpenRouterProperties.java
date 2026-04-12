package com.jobtracker.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        @NotBlank String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String model,
        @NotBlank String siteUrl
) {}
