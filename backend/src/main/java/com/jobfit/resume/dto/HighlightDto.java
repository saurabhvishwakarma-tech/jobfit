package com.jobfit.resume.dto;

import jakarta.validation.constraints.NotBlank;

public record HighlightDto(@NotBlank String text) {
}
