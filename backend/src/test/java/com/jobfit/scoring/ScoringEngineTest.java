package com.jobfit.scoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jobfit.scoring.ScoringModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The scoring algorithm is the highest-value, highest-risk piece of logic
 * in the whole application, so it gets a fully hand-traced fixture: every
 * expected number below was computed by hand by walking ScoringEngine's
 * actual rules (see the PR/commit notes), not guessed and adjusted until
 * green. Component point assertions use a small tolerance because the
 * engine rounds intermediate values to 2 decimal places using floating
 * point division.
 */
class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();

    private ScoringInput buildInput() {
        List<RequirementInput> required = List.of(
                new RequirementInput(1L, "Java", 1L),
                new RequirementInput(2L, "Spring Boot", 2L),
                new RequirementInput(3L, "Docker", 3L));
        List<RequirementInput> preferred = List.of(
                new RequirementInput(4L, "AWS", 4L));
        List<RequirementInput> responsibilities = List.of(
                new RequirementInput(5L, "Design and build REST APIs", null),
                new RequirementInput(6L, "Mentor junior engineers", null));
        List<RequirementInput> education = List.of(
                new RequirementInput(10L, "Bachelor's degree required", null));
        List<RequirementInput> softSkills = List.of(
                new RequirementInput(7L, "Communication", 5L));

        List<HighlightInput> highlights = List.of(
                new HighlightInput(100L, "Designed and built REST APIs using Spring Boot"),
                new HighlightInput(101L, "Mentored two junior engineers on the team"));

        List<ResumeSkillInput> skills = List.of(
                new ResumeSkillInput(1L, "Java", ResumeSkillSource.EXPLICIT, null),
                new ResumeSkillInput(2L, "Spring Boot", ResumeSkillSource.EXPLICIT, null),
                new ResumeSkillInput(4L, "AWS", ResumeSkillSource.INFERRED, null));
        // Note: skillId 3 (Docker) and 5 (Communication) are deliberately absent.

        List<EducationInput> educationEntries = List.of(
                new EducationInput(200L, "BSc Computer Science", 1));

        ResumeProfile resume = new ResumeProfile(
                6.0, List.of("Backend Engineer"), highlights, skills, educationEntries);

        return new ScoringInput("Senior Backend Engineer", required, preferred, responsibilities,
                List.of(), education, softSkills, 5, 20L, 1, resume);
    }

    @Test
    void computesOverallScoreAndRecommendation() {
        ScoringResult result = engine.score(buildInput());

        assertThat(result.overallScore()).isEqualTo(63);
        assertThat(result.recommendation()).isEqualTo("REASONABLE_MATCH");
        assertThat(result.recommendationReason()).contains("2/3 required skills");
    }

    @Test
    void requiredSkillsComponent_explicitMatchesAndOneMissing() {
        ScoringResult result = engine.score(buildInput());
        ScoreComponentResult required = componentNamed(result, "Required skills");

        assertThat(required.maxPoints()).isEqualTo(35.0);
        assertThat(required.earnedPoints()).isCloseTo(23.33, within(0.01));
        assertThat(required.explanation()).isEqualTo("2 strong, 0 partial, 1 missing (out of 3).");
    }

    @Test
    void preferredSkillsComponent_inferredMatchGetsPartialCredit() {
        ScoringResult result = engine.score(buildInput());
        ScoreComponentResult preferred = componentNamed(result, "Preferred skills");

        assertThat(preferred.earnedPoints()).isCloseTo(6.0, within(0.01));
        assertThat(preferred.explanation()).isEqualTo("0 strong, 1 partial, 0 missing (out of 1).");
    }

    @Test
    void experienceComponent_blendsYearsRatioAndTitleRelevance() {
        ScoringResult result = engine.score(buildInput());
        ScoreComponentResult experience = componentNamed(result, "Experience");

        // yearsRatio = min(1, 6/5) = 1.0 (capped); titleRelevance = jaccard("senior backend engineer", "backend engineer") = 2/3
        // fraction = 0.6*1.0 + 0.4*(2/3) = 0.86667; earned = 0.86667 * 20 = 17.33
        assertThat(experience.earnedPoints()).isCloseTo(17.33, within(0.01));
    }

    @Test
    void responsibilitiesComponent_usesLexicalSimilarityToHighlights() {
        ScoringResult result = engine.score(buildInput());
        ScoreComponentResult responsibilities = componentNamed(result, "Responsibilities & domain");

        // Both responsibility lines partially overlap resume highlights (0.25 and 0.4) -> avg 0.325 * 20 = 6.5
        assertThat(responsibilities.earnedPoints()).isCloseTo(6.5, within(0.01));
    }

    @Test
    void educationComponent_fullCreditWhenLevelMet() {
        ScoringResult result = engine.score(buildInput());
        ScoreComponentResult education = componentNamed(result, "Education");

        assertThat(education.earnedPoints()).isCloseTo(10.0, within(0.01));
    }

    @Test
    void softSkillsComponent_noCreditWhenNotMentioned() {
        ScoringResult result = engine.score(buildInput());
        ScoreComponentResult softSkills = componentNamed(result, "Soft skills");

        assertThat(softSkills.earnedPoints()).isCloseTo(0.0, within(0.01));
    }

    @Test
    void producesEvidenceForEveryRequirementPlusExperience() {
        ScoringResult result = engine.score(buildInput());

        // 3 required + 1 preferred + 0 domain + 1 soft skill + 2 responsibilities + 1 education + 1 experience = 9
        assertThat(result.evidence()).hasSize(9);

        EvidenceResult dockerEvidence = result.evidence().stream()
                .filter(e -> e.requirementId().equals(3L)).findFirst().orElseThrow();
        assertThat(dockerEvidence.matchType()).isEqualTo(MatchType.ABSENT);
        assertThat(dockerEvidence.strength()).isEqualTo(EvidenceStrength.MISSING);

        EvidenceResult javaEvidence = result.evidence().stream()
                .filter(e -> e.requirementId().equals(1L)).findFirst().orElseThrow();
        assertThat(javaEvidence.matchType()).isEqualTo(MatchType.EXPLICIT);
        assertThat(javaEvidence.strength()).isEqualTo(EvidenceStrength.STRONG);

        EvidenceResult experienceEvidence = result.evidence().stream()
                .filter(e -> e.requirementId().equals(20L)).findFirst().orElseThrow();
        assertThat(experienceEvidence.matchType()).isEqualTo(MatchType.EXPLICIT);
    }

    @Test
    void emptyRequirementCategory_givesFullCreditWithExplanation() {
        ScoringInput input = new ScoringInput("Engineer", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, null, 0,
                new ResumeProfile(2.0, List.of(), List.of(), List.of(), List.of()));

        ScoringResult result = engine.score(input);

        ScoreComponentResult required = componentNamed(result, "Required skills");
        assertThat(required.earnedPoints()).isEqualTo(35.0);
        assertThat(required.explanation()).contains("No required skills were clearly identified");
    }

    @Test
    void missingRequiredSkill_capsWhatWouldBeStrongMatchDownToStretch() {
        // 4 of 5 required skills explicitly matched (28/35), every other category full credit.
        // Sum = 28 + 10 + 20 + 20 + 10 + 5 = 93, which would be STRONG_MATCH on score alone -
        // but one required skill ("Docker") is still missing, so the recommendation must be
        // capped down rather than telling the user to apply as if they meet every must-have.
        List<RequirementInput> required = List.of(
                new RequirementInput(1L, "Java", 1L),
                new RequirementInput(2L, "Python", 2L),
                new RequirementInput(3L, "SQL", 3L),
                new RequirementInput(4L, "Go", 4L),
                new RequirementInput(5L, "Docker", 5L));
        List<ResumeSkillInput> skills = List.of(
                new ResumeSkillInput(1L, "Java", ResumeSkillSource.EXPLICIT, null),
                new ResumeSkillInput(2L, "Python", ResumeSkillSource.EXPLICIT, null),
                new ResumeSkillInput(3L, "SQL", ResumeSkillSource.EXPLICIT, null),
                new ResumeSkillInput(4L, "Go", ResumeSkillSource.EXPLICIT, null));
        ResumeProfile resume = new ResumeProfile(10.0, List.of("Engineer"), List.of(), skills, List.of());
        ScoringInput input = new ScoringInput("Engineer", required, List.of(), List.of(), List.of(), List.of(),
                List.of(), null, null, 0, resume);

        ScoringResult result = engine.score(input);

        assertThat(result.overallScore()).isGreaterThanOrEqualTo(80);
        assertThat(result.recommendation()).isEqualTo("STRETCH_APPLICATION");
    }

    private ScoreComponentResult componentNamed(ScoringResult result, String name) {
        return result.components().stream().filter(c -> c.category().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No component named " + name));
    }
}
