package com.jobfit.application.dto;

import jakarta.validation.constraints.NotNull;

public record ApplicationCreateRequest(
        @NotNull Long jobId,
        Long resumeId,
        Long matchAnalysisId,
        String notes
) {
}
