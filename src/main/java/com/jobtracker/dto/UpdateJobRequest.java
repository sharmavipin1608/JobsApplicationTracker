package com.jobtracker.dto;

import com.jobtracker.enums.JobStatus;

public record UpdateJobRequest(
        JobStatus status,
        String notes,
        String jdUrl
) {}
