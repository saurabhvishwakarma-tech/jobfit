package com.jobfit.jobparsing;

import org.junit.jupiter.api.Test;

import static com.jobfit.jobparsing.JobExtractionModels.Bucket;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Like DeterministicResumeExtractorTest, this fixture and its assertions
 * were derived by hand-tracing the actual section-splitting and
 * reclassification logic - including the known limitation that trailing
 * prose after the last recognized section header gets swept into that
 * section (see the final RESPONSIBILITY assertion below), which is
 * documented behavior, not an oversight.
 */
class DeterministicJobExtractorTest {

    private final DeterministicJobExtractor extractor = new DeterministicJobExtractor();

    private static final String SAMPLE_JD = """
            Acme Corp is looking for a Backend Engineer to join our growing platform team.

            Requirements
            - 5+ years of experience in backend development
            - Strong experience with Java and Spring Boot
            - Familiarity with PostgreSQL
            - Bachelor's degree in Computer Science or related field

            Preferred
            - Experience with AWS
            - Knowledge of Docker

            Responsibilities
            - Design and build REST APIs
            - Collaborate with the frontend team
            - Mentor junior engineers

            We value strong communication and teamwork.
            """;

    @Test
    void classifiesRequiredLines() {
        var result = extractor.extract(SAMPLE_JD);
        var required = result.lines().stream().filter(l -> l.bucket() == Bucket.REQUIRED).toList();

        assertThat(required).hasSize(3);
        assertThat(required.stream().map(JobExtractionModels.RequirementLine::text)).containsExactly(
                "5+ years of experience in backend development",
                "Strong experience with Java and Spring Boot",
                "Familiarity with PostgreSQL");
    }

    @Test
    void reclassifiesDegreeLineFromRequiredToEducation() {
        var result = extractor.extract(SAMPLE_JD);
        var education = result.lines().stream().filter(l -> l.bucket() == Bucket.EDUCATION).toList();

        assertThat(education).hasSize(1);
        assertThat(education.get(0).text()).isEqualTo("Bachelor's degree in Computer Science or related field");
    }

    @Test
    void classifiesPreferredLines() {
        var result = extractor.extract(SAMPLE_JD);
        var preferred = result.lines().stream().filter(l -> l.bucket() == Bucket.PREFERRED).toList();

        assertThat(preferred.stream().map(JobExtractionModels.RequirementLine::text))
                .containsExactly("Experience with AWS", "Knowledge of Docker");
    }

    @Test
    void classifiesResponsibilityLines() {
        var result = extractor.extract(SAMPLE_JD);
        var responsibilities = result.lines().stream().filter(l -> l.bucket() == Bucket.RESPONSIBILITY).toList();

        assertThat(responsibilities.stream().map(JobExtractionModels.RequirementLine::text)).containsExactly(
                "Design and build REST APIs",
                "Collaborate with the frontend team",
                "Mentor junior engineers",
                "We value strong communication and teamwork.");
    }

    @Test
    void extractsExperienceYearsSnippet() {
        var result = extractor.extract(SAMPLE_JD);

        assertThat(result.experienceYearsSnippet()).isEqualTo("- 5+ years of experience in backend development");
    }

    @Test
    void ignoresUnsectionedIntroText() {
        var result = extractor.extract(SAMPLE_JD);

        assertThat(result.lines()).noneMatch(l -> l.text().contains("Acme Corp is looking for"));
    }

    @Test
    void returnsEmptyOnTextWithNoRecognizedSections() {
        var result = extractor.extract("Just some unstructured text about a role with no headers.");

        assertThat(result.lines()).isEmpty();
        assertThat(result.experienceYearsSnippet()).isNull();
    }
}
