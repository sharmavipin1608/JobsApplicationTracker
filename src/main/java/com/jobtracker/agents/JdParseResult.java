package com.jobtracker.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JdParseResult(
        List<String> skills,
        String seniority,
        String domain
) {}
