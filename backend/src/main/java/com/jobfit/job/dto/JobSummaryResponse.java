package com.jobfit.job.dto;

import com.jobfit.job.Job;

import java.time.Instant;

public record JobSummaryResponse(
        Long id, String title, String company, String parseStatus, String parseError,
        Instant createdAt, Instant parsedAt) {

    public static JobSummaryResponse from(Job job) {
        return new JobSummaryResponse(job.getId(), job.getTitle(), job.getCompany(),
                job.getParseStatus().name(), job.getParseError(), job.getCreatedAt(), job.getParsedAt());
    }
}
