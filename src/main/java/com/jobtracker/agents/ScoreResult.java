package com.jobtracker.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreResult(
        @JsonProperty("fit_score") int fitScore,
        List<String> recommendations
) {}
