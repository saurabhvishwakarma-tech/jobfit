package com.jobfit.resume.dto;

import java.time.LocalDate;

public record EducationDto(
        Long id, String institution, String degree, String fieldOfStudy,
        LocalDate startDate, LocalDate endDate) {
}
