package com.jobfit.matching.dto;

import com.jobfit.matching.MatchAnalysis;

import java.time.Instant;

public record MatchAnalysisSummaryResponse(
        Long id, Long resumeId, Long jobId, int overallScore, String recommendation, Instant createdAt) {

    public static MatchAnalysisSummaryResponse from(MatchAnalysis m) {
        return new MatchAnalysisSummaryResponse(
                m.getId(), m.getResumeId(), m.getJobId(), m.getOverallScore(), m.getRecommendation(), m.getCreatedAt());
    }
}
