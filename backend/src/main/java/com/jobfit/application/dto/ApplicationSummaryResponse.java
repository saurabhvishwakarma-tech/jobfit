package com.jobfit.application.dto;

import java.time.Instant;

public record ApplicationSummaryResponse(
        Long id, Long jobId, String jobTitle, String company,
        Long resumeId, Integer matchScore, String status,
        Instant appliedAt, Instant updatedAt) {
}
