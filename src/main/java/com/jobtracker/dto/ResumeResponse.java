package com.jobtracker.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ResumeResponse(
        UUID id,
        UUID jobId,
        String fileName,
        String contentText,
        LocalDateTime createdAt
) {}
