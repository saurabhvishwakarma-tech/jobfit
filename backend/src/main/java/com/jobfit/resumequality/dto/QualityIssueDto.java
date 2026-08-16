package com.jobfit.resumequality.dto;

public record QualityIssueDto(
        String category, String severity, String message,
        String resumeRefType, Long resumeRefId) {
}
