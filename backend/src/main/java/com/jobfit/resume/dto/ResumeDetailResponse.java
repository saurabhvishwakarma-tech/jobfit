package com.jobfit.resume.dto;

import com.jobfit.resume.Resume;

import java.time.Instant;
import java.util.List;

public record ResumeDetailResponse(
        Long id, int versionNo, boolean current, String originalFilename,
        String parseStatus, String parseError, Instant uploadedAt, Instant parsedAt,
        ContactInfoDto contactInfo,
        List<ExperienceDto> experiences,
        List<EducationDto> education,
        List<CertificationDto> certifications,
        List<ProjectDto> projects,
        List<SkillTagDto> skills) {

    public static ResumeDetailResponse of(Resume r, ContactInfoDto contactInfo,
                                           List<ExperienceDto> experiences, List<EducationDto> education,
                                           List<CertificationDto> certifications, List<ProjectDto> projects,
                                           List<SkillTagDto> skills) {
        return new ResumeDetailResponse(
                r.getId(), r.getVersionNo(), r.isCurrent(), r.getOriginalFilename(),
                r.getParseStatus().name(), r.getParseError(), r.getUploadedAt(), r.getParsedAt(),
                contactInfo, experiences, education, certifications, projects, skills);
    }
}
