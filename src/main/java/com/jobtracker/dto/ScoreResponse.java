package com.jobtracker.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScoreResponse(
        UUID id,
        UUID jobId,
        Integer fitScore,
        @JsonRawValue String recommendations,
        LocalDateTime createdAt
) {}
