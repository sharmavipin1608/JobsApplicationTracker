package com.jobtracker.dto;

import com.jobtracker.enums.JobStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String company,
        String role,
        JobStatus status,
        LocalDateTime appliedAt,
        String notes,
        String jdText,
        String jdUrl,
        Integer fitScore,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
