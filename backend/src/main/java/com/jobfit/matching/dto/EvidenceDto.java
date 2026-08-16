package com.jobfit.matching.dto;

public record EvidenceDto(
        Long requirementId, String requirementType, String requirementText,
        String matchType, String strength,
        String resumeRefType, Long resumeRefId, String resumeRefText,
        String explanationText, Double confidence) {
}
