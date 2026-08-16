package com.jobfit.matching.dto;

import java.util.List;

/**
 * One column in a job comparison table. `analysed` is false when the job
 * hasn't been scored against the user's resume yet - in that case
 * matchAnalysisId/overallScore/recommendation/categoryScores are all null
 * or empty rather than guessed at.
 */
public record ComparedJobDto(
        Long jobId, String title, String company, boolean analysed,
        Long matchAnalysisId, Integer overallScore, String recommendation,
        List<ScoreComponentDto> categoryScores) {
}
