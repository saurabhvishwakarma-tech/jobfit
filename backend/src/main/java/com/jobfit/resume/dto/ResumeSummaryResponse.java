package com.jobfit.resume.dto;

import com.jobfit.resume.Resume;

import java.time.Instant;

public record ResumeSummaryResponse(
        Long id, int versionNo, boolean current, String originalFilename,
        String parseStatus, String parseError, Instant uploadedAt, Instant parsedAt) {

    public static ResumeSummaryResponse from(Resume r) {
        return new ResumeSummaryResponse(
                r.getId(), r.getVersionNo(), r.isCurrent(), r.getOriginalFilename(),
                r.getParseStatus().name(), r.getParseError(), r.getUploadedAt(), r.getParsedAt());
    }
}
