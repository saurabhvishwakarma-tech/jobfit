package com.jobfit.resume.dto;

public record ContactInfoDto(
        String fullName, String email, String phone, String location,
        String linkedinUrl, String githubUrl, String portfolioUrl) {
}
