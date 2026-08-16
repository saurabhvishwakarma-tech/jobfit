package com.jobfit.application.dto;

import java.time.Instant;

public record StatusHistoryDto(String status, String notes, Instant changedAt) {
}
