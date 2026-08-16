package com.jobfit.resumeparsing;

import java.time.LocalDate;
import java.util.List;

/**
 * Plain, non-persistent models produced by extraction, before they're
 * mapped onto JPA entities. Kept separate from the resume module's entities
 * so the parsing pipeline can be tested (and reasoned about) without a
 * database at all.
 */
public final class ExtractionModels {

    private ExtractionModels() {
    }

    public record ContactInfo(
            String fullName, String email, String phone, String location,
            String linkedinUrl, String githubUrl, String portfolioUrl) {
    }

    public record Experience(
            String jobTitle, String company, String location,
            LocalDate startDate, LocalDate endDate, boolean current,
            List<String> highlights) {
    }

    public record Education(
            String institution, String degree, String fieldOfStudy,
            LocalDate startDate, LocalDate endDate) {
    }

    public record Certification(String name, String issuer, LocalDate issuedDate) {
    }

    public record Project(String name, String description, String technologies) {
    }

    public record ExtractionResult(
            ContactInfo contactInfo,
            List<Experience> experiences,
            List<Education> education,
            List<Certification> certifications,
            List<Project> projects,
            List<String> skillTerms) {
    }
}
