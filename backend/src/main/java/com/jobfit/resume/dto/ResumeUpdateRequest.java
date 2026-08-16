package com.jobfit.resume.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Full-replace update: the frontend sends back the entire edited structure
 * (contact info + all lists) and the service replaces the corresponding
 * child rows wholesale. Simpler and safer than diffing individual fields,
 * and matches how the "review and correct the parse" screen actually works
 * - the user is looking at and editing the whole resume at once.
 */
public record ResumeUpdateRequest(
        @Valid @NotNull ContactInfoDto contactInfo,
        @Valid @NotNull List<ExperienceDto> experiences,
        @Valid @NotNull List<EducationDto> education,
        @Valid @NotNull List<CertificationDto> certifications,
        @Valid @NotNull List<ProjectDto> projects
) {
}
