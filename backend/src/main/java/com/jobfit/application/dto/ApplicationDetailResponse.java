package com.jobfit.application.dto;

import java.time.Instant;
import java.util.List;

public record ApplicationDetailResponse(
        Long id, Long jobId, String jobTitle, String company,
        Long resumeId, Long matchAnalysisId, Integer matchScore, String matchRecommendation,
        String status, String notes, Instant appliedAt, Instant createdAt, Instant updatedAt,
        List<StatusHistoryDto> history) {
}
