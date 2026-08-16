package com.jobfit.matching.dto;

import java.time.Instant;
import java.util.List;

public record MatchAnalysisDetailResponse(
        Long id, Long resumeId, Long jobId, String jobTitle, String company,
        int overallScore, String recommendation, String recommendationReason, Instant createdAt,
        List<ScoreComponentDto> components,
        List<EvidenceDto> evidence) {
}
