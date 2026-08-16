package com.jobfit.resumequality;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jobfit.resumequality.QualityModels.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResumeQualityAnalyzer is a pure function - every case here hand-traces
 * exactly which rules should fire and what the resulting score must be,
 * the same discipline used for ScoringEngineTest. Constructed directly
 * with `new ResumeQualityAnalyzer()`, zero Spring context.
 */
class ResumeQualityAnalyzerTest {

    private final ResumeQualityAnalyzer analyzer = new ResumeQualityAnalyzer();

    @Test
    void cleanResume_scoresPerfectWithNoIssues() {
        QualityInput input = new QualityInput(
                true, true,
                List.of(new ExperienceInput(1L, "Software Engineer", 3)),
                5,
                List.of(
                        new BulletInput(1L, "Reduced API response time by 40% through caching improvements."),
                        new BulletInput(2L, "Migrated 12 legacy services to Kubernetes, cutting deployment time in half."),
                        new BulletInput(3L, "Mentored 3 junior engineers and led weekly code review sessions.")));

        QualityResult result = analyzer.analyze(input);

        assertThat(result.issues()).isEmpty();
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void missingContactInfoAndEmptyResume_flagsExpectedSeverities() {
        QualityInput input = new QualityInput(false, false, List.of(), 0, List.of());

        QualityResult result = analyzer.analyze(input);

        // Contact Info: no email (HIGH), no phone (MEDIUM).
        // Structure: no experience (HIGH), no skills (MEDIUM), fewer than 3 total bullets (MEDIUM).
        assertThat(result.issues()).hasSize(5);
        assertThat(result.issues().stream().filter(i -> i.severity() == IssueSeverity.HIGH)).hasSize(2);
        assertThat(result.issues().stream().filter(i -> i.severity() == IssueSeverity.MEDIUM)).hasSize(3);
        assertThat(result.issues().stream().filter(i -> i.severity() == IssueSeverity.LOW)).isEmpty();
        // 100 - (2*10 + 3*5) = 100 - 35 = 65
        assertThat(result.score()).isEqualTo(65);
    }

    @Test
    void bulletWithWeakPhrasingBuzzwordAndFirstPerson_flagsAllThreePlusMissingQuantification() {
        String problemBullet = "I was responsible for being a team player on my projects";
        QualityInput input = new QualityInput(
                true, true,
                List.of(new ExperienceInput(1L, "Engineer", 3)),
                2,
                List.of(
                        new BulletInput(1L, "Reduced latency by 20% across the platform."),
                        new BulletInput(2L, problemBullet),
                        new BulletInput(3L, "Delivered 5 major features on schedule.")));

        QualityResult result = analyzer.analyze(input);

        // From the problem bullet only: missing quantification (LOW), weak phrasing (MEDIUM),
        // buzzword (LOW), first-person pronoun (LOW). The two clean bullets contribute nothing.
        assertThat(result.issues()).hasSize(4);
        assertThat(result.issues()).allMatch(i -> i.resumeRefId().equals(2L));
        assertThat(result.issues()).extracting("category")
                .containsExactlyInAnyOrder("Impact", "Phrasing", "Phrasing", "Phrasing");
        assertThat(result.issues().stream().filter(i -> i.severity() == IssueSeverity.MEDIUM)).hasSize(1);
        assertThat(result.issues().stream().filter(i -> i.severity() == IssueSeverity.LOW)).hasSize(3);
        // 100 - (1*5 + 3*2) = 89
        assertThat(result.score()).isEqualTo(89);
    }

    @Test
    void bulletTooShortAndTooLong_flagsReadabilityOnly() {
        String shortBullet = "Coded stuff"; // 11 chars, under the 15-char floor
        String longBullet = "Delivered ".repeat(25) + "5 releases."; // 261 chars, well over the 220-char ceiling
        assertThat(longBullet.length()).isGreaterThan(220); // sanity-check the fixture itself

        QualityInput input = new QualityInput(
                true, true,
                List.of(new ExperienceInput(1L, "Engineer", 3)),
                1,
                List.of(
                        new BulletInput(1L, shortBullet),
                        new BulletInput(2L, longBullet),
                        new BulletInput(3L, "Automated deployment pipelines, reducing release time by 30%.")));

        QualityResult result = analyzer.analyze(input);

        assertThat(result.issues()).hasSize(2);
        assertThat(result.issues()).allMatch(i -> i.category().equals("Readability"));
        assertThat(result.issues()).allMatch(i -> i.severity() == IssueSeverity.LOW);
        // 100 - (2*2) = 96
        assertThat(result.score()).isEqualTo(96);
    }

    @Test
    void repeatedLeadingVerbAcrossMostBullets_flagsVariety() {
        QualityInput input = new QualityInput(
                true, true,
                List.of(new ExperienceInput(1L, "Engineer", 4)),
                1,
                List.of(
                        new BulletInput(1L, "Managed a team of 5 engineers."),
                        new BulletInput(2L, "Managed the budget of $200,000."),
                        new BulletInput(3L, "Managed vendor relationships across 3 regions."),
                        new BulletInput(4L, "Managed release planning for 10 sprints.")));

        QualityResult result = analyzer.analyze(input);

        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).category()).isEqualTo("Variety");
        assertThat(result.issues().get(0).severity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(result.issues().get(0).message()).contains("managed");
        // 100 - 5 = 95
        assertThat(result.score()).isEqualTo(95);
    }

    @Test
    void underThreeBulletVarietyCheckIsSkipped_evenWithIdenticalLeadingVerbs() {
        QualityInput input = new QualityInput(
                true, true,
                List.of(new ExperienceInput(1L, "Engineer", 3)),
                1,
                List.of(
                        new BulletInput(1L, "Managed a team of 5 engineers."),
                        new BulletInput(2L, "Managed the budget of $200,000."),
                        new BulletInput(3L, "Managed vendor relationships across 3 regions.")));

        QualityResult result = analyzer.analyze(input);

        // Only 3 bullets - below MIN_BULLETS_FOR_VARIETY_CHECK (4) - so the repeated-verb
        // rule must not fire even though all three start with "Managed".
        assertThat(result.issues()).isEmpty();
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void scoreNeverGoesBelowZero() {
        List<ExperienceInput> experiences = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            experiences.add(new ExperienceInput((long) i, "Role " + i, 0));
        }
        QualityInput input = new QualityInput(false, false, experiences, 0, List.of());

        QualityResult result = analyzer.analyze(input);

        assertThat(result.score()).isZero();
    }

    @Test
    void experienceWithZeroHighlights_isFlaggedByTitle() {
        QualityInput input = new QualityInput(
                true, true,
                List.of(
                        new ExperienceInput(1L, "Software Engineer", 3),
                        new ExperienceInput(2L, "Intern", 0)),
                2,
                List.of(
                        new BulletInput(1L, "Shipped 4 features across two quarters."),
                        new BulletInput(2L, "Reduced bug backlog by 25% in one sprint."),
                        new BulletInput(3L, "Onboarded 6 new hires onto the platform.")));

        QualityResult result = analyzer.analyze(input);

        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).category()).isEqualTo("Structure");
        assertThat(result.issues().get(0).resumeRefType()).isEqualTo("EXPERIENCE");
        assertThat(result.issues().get(0).resumeRefId()).isEqualTo(2L);
        assertThat(result.issues().get(0).message()).contains("Intern");
    }
}
