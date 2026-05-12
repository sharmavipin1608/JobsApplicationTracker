package com.jobtracker.dto;

import com.jobtracker.enums.JobStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record CreateJobRequest(
        @NotBlank String company,
        @NotBlank String role,
        String jdText,
        String jdUrl,
        String notes,
        JobStatus status,
        LocalDateTime appliedAt
) {}
