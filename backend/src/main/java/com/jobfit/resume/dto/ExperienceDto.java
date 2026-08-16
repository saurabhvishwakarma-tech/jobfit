package com.jobfit.resume.dto;

import java.time.LocalDate;
import java.util.List;

public record ExperienceDto(
        Long id, String jobTitle, String company, String location,
        LocalDate startDate, LocalDate endDate, boolean current,
        List<HighlightDto> highlights) {
}
