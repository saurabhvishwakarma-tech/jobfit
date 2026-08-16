package com.jobfit.job.dto;

import com.jobfit.job.Job;

import java.time.Instant;
import java.util.List;

public record JobDetailResponse(
        Long id, String title, String company, String rawDescription, String sourceUrl,
        String parseStatus, String parseError, Instant createdAt, Instant parsedAt,
        List<JobRequirementDto> requiredSkills,
        List<JobRequirementDto> preferredSkills,
        List<JobRequirementDto> responsibilities,
        List<JobRequirementDto> education,
        List<JobRequirementDto> domain,
        List<JobRequirementDto> softSkills,
        JobRequirementDto experienceYears) {

    public static JobDetailResponse of(Job job, List<JobRequirementDto> requiredSkills,
                                        List<JobRequirementDto> preferredSkills, List<JobRequirementDto> responsibilities,
                                        List<JobRequirementDto> education, List<JobRequirementDto> domain,
                                        List<JobRequirementDto> softSkills, JobRequirementDto experienceYears) {
        return new JobDetailResponse(job.getId(), job.getTitle(), job.getCompany(), job.getRawDescription(),
                job.getSourceUrl(), job.getParseStatus().name(), job.getParseError(), job.getCreatedAt(),
                job.getParsedAt(), requiredSkills, preferredSkills, responsibilities, education, domain,
                softSkills, experienceYears);
    }
}
