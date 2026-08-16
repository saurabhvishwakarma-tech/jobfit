package com.jobfit.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ApplicationStatusUpdateRequest(
        @NotBlank String status,
        String notes
) {
}
