package com.jobfit.resumequality.dto;

import java.util.List;

public record ResumeQualityResponse(
        Long resumeId, int score, int highCount, int mediumCount, int lowCount,
        List<QualityIssueDto> issues) {
}
