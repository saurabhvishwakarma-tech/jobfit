package com.jobfit.resumeats;

import java.util.List;

/**
 * Plain input/output models for AtsScoreAnalyzer - deliberately free of any
 * JPA entity or Spring dependency, same discipline as
 * resumequality.QualityModels. AtsScoreService is responsible for
 * translating entities/raw text into these records.
 */
public final class AtsModels {

    private AtsModels() {
    }

    // ---------- Input ----------

    public record ExperienceDateInput(boolean hasStartDate) {
    }

    public record AtsInput(
            boolean hasEmail,
            boolean hasPhone,
            int experienceCount,
            int educationCount,
            int skillCount,
            int wordCount,
            List<ExperienceDateInput> experienceDates,
            double nonStandardCharRatio) {
    }

    // ---------- Output ----------

    public record AtsCheck(String label, AtsCheckStatus status, String detail) {
    }

    public record AtsResult(int score, List<AtsCheck> checks) {
    }
}
