package com.jobfit.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String company,
        @NotBlank @Size(max = 20000) String rawDescription,
        @Size(max = 1000) String sourceUrl
) {
}
