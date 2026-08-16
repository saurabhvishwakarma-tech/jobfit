package com.jobfit.resumeparsing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deterministic extractor is the highest-risk, highest-value piece of
 * Phase 2 to get right - it's pure logic (no Spring context, no I/O), so it
 * gets a real, traced-through fixture rather than a trivial smoke test.
 * Every assertion below was hand-derived by walking the actual regex/section
 * logic against this exact fixture text.
 */
class DeterministicResumeExtractorTest {

    private final DeterministicResumeExtractor extractor = new DeterministicResumeExtractor();

    private static final String SAMPLE_RESUME = """
            Jane Doe
            jane.doe@example.com | +1 555-123-4567
            linkedin.com/in/janedoe | github.com/janedoe

            EXPERIENCE

            Senior Software Engineer - Acme Corp
            Jan 2021 - Present
            Built backend services using Java and Spring Boot.
            Led a team of four engineers.

            Software Engineer - Beta Inc
            Jun 2018 - Dec 2020
            Developed REST APIs in Python and Django.

            EDUCATION

            University of Dublin, BSc, Computer Science
            Sep 2014 - Jun 2018

            SKILLS

            Java, Spring Boot, Python, Docker, AWS

            CERTIFICATIONS

            AWS Certified Solutions Architect - Amazon Web Services

            PROJECTS

            Personal Finance Tracker
            A web app to track personal expenses.
            Technologies: React, Node.js
            """;

    @Test
    void extractsContactInfo() {
        var result = extractor.extract(SAMPLE_RESUME);

        assertThat(result.contactInfo().fullName()).isEqualTo("Jane Doe");
        assertThat(result.contactInfo().email()).isEqualTo("jane.doe@example.com");
        assertThat(result.contactInfo().phone()).isNotNull();
        assertThat(result.contactInfo().linkedinUrl()).contains("linkedin.com/in/janedoe");
        assertThat(result.contactInfo().githubUrl()).contains("github.com/janedoe");
    }

    @Test
    void extractsTwoExperienceEntriesWithDatesAndHighlights() {
        var result = extractor.extract(SAMPLE_RESUME);

        assertThat(result.experiences()).hasSize(2);

        var first = result.experiences().get(0);
        assertThat(first.jobTitle()).isEqualTo("Senior Software Engineer");
        assertThat(first.company()).isEqualTo("Acme Corp");
        assertThat(first.startDate()).isEqualTo(LocalDate.of(2021, 1, 1));
        assertThat(first.endDate()).isNull();
        assertThat(first.current()).isTrue();
        assertThat(first.highlights()).containsExactly(
                "Built backend services using Java and Spring Boot.",
                "Led a team of four engineers.");

        var second = result.experiences().get(1);
        assertThat(second.jobTitle()).isEqualTo("Software Engineer");
        assertThat(second.company()).isEqualTo("Beta Inc");
        assertThat(second.startDate()).isEqualTo(LocalDate.of(2018, 6, 1));
        assertThat(second.endDate()).isEqualTo(LocalDate.of(2020, 12, 1));
        assertThat(second.current()).isFalse();
    }

    @Test
    void extractsEducationWithDegreeAndFieldSplitFromCommaList() {
        var result = extractor.extract(SAMPLE_RESUME);

        assertThat(result.education()).hasSize(1);
        var edu = result.education().get(0);
        assertThat(edu.institution()).isEqualTo("University of Dublin");
        assertThat(edu.degree()).isEqualTo("BSc");
        assertThat(edu.fieldOfStudy()).isEqualTo("Computer Science");
        assertThat(edu.startDate()).isEqualTo(LocalDate.of(2014, 9, 1));
        assertThat(edu.endDate()).isEqualTo(LocalDate.of(2018, 6, 1));
    }

    @Test
    void extractsSkillTermsAsIndividualTags() {
        var result = extractor.extract(SAMPLE_RESUME);

        assertThat(result.skillTerms()).containsExactly("Java", "Spring Boot", "Python", "Docker", "AWS");
    }

    @Test
    void extractsCertificationWithIssuer() {
        var result = extractor.extract(SAMPLE_RESUME);

        assertThat(result.certifications()).hasSize(1);
        var cert = result.certifications().get(0);
        assertThat(cert.name()).isEqualTo("AWS Certified Solutions Architect");
        assertThat(cert.issuer()).isEqualTo("Amazon Web Services");
    }

    @Test
    void extractsProjectWithDescriptionAndTechnologies() {
        var result = extractor.extract(SAMPLE_RESUME);

        assertThat(result.projects()).hasSize(1);
        var project = result.projects().get(0);
        assertThat(project.name()).isEqualTo("Personal Finance Tracker");
        assertThat(project.description()).isEqualTo("A web app to track personal expenses.");
        assertThat(project.technologies()).isEqualTo("React, Node.js");
    }

    @Test
    void doesNotBlowUpOnEmptyOrMinimalText() {
        var result = extractor.extract("Just a name\nno structure here at all");

        assertThat(result.experiences()).isEmpty();
        assertThat(result.education()).isEmpty();
        assertThat(result.skillTerms()).isEmpty();
    }
}
