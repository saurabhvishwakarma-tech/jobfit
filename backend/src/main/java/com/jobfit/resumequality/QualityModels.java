package com.jobfit.resumequality;

import java.util.List;

/**
 * Plain input/output models for ResumeQualityAnalyzer - deliberately free
 * of any JPA entity or Spring dependency, same discipline as
 * `scoring.ScoringModels` (see docs/JobFit_Design_v1.md, "Resume Quality
 * Analysis as a standalone feature"). ResumeQualityService is responsible
 * for translating entities into these records.
 */
public final class QualityModels {

    private QualityModels() {
    }

    // ---------- Input ----------

    public record BulletInput(Long id, String text) {
    }

    public record ExperienceInput(Long id, String jobTitle, int highlightCount) {
    }

    public record QualityInput(
            boolean hasEmail,
            boolean hasPhone,
            List<ExperienceInput> experiences,
            int skillCount,
            List<BulletInput> allHighlights) {
    }

    // ---------- Output ----------

    /**
     * `resumeRefType`/`resumeRefId` point at the exact experience or bullet
     * this issue is about, so the UI can link back to it - resume-level or
     * cross-cutting issues (e.g. verb repetition) leave both null rather
     * than pointing at an arbitrary bullet.
     */
    public record QualityIssue(
            String category, IssueSeverity severity, String message,
            String resumeRefType, Long resumeRefId) {
    }

    public record QualityResult(int score, List<QualityIssue> issues) {
    }
}
