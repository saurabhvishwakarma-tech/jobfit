package com.jobfit.scoring;

import java.util.List;

/**
 * Plain input/output models for ScoringEngine - deliberately free of any
 * JPA entity or Spring dependency so the engine can be constructed and
 * tested with zero I/O (see docs/JobFit_Design_v1.md, Testing Strategy:
 * "scoring algorithm as pure, table-driven tests"). The `matching` module
 * is responsible for translating entities into these records and back.
 */
public final class ScoringModels {

    private ScoringModels() {
    }

    // ---------- Input ----------

    public record RequirementInput(Long requirementId, String text, Long skillId) {
    }

    public record HighlightInput(Long id, String text) {
    }

    public record ResumeSkillInput(Long skillId, String name, ResumeSkillSource source, Long evidenceHighlightId) {
    }

    /** levelOrdinal: 0=unspecified/other, 1=Bachelor's, 2=Master's, 3=Doctorate. */
    public record EducationInput(Long id, String description, int levelOrdinal) {
    }

    public record ResumeProfile(
            double totalYearsExperience,
            List<String> experienceJobTitles,
            List<HighlightInput> highlights,
            List<ResumeSkillInput> skills,
            List<EducationInput> education) {
    }

    public record ScoringInput(
            String jobTitle,
            List<RequirementInput> requiredSkills,
            List<RequirementInput> preferredSkills,
            List<RequirementInput> responsibilities,
            List<RequirementInput> domainRequirements,
            List<RequirementInput> educationRequirements,
            List<RequirementInput> softSkillRequirements,
            Integer requiredYearsExperience,
            /** id of the EXPERIENCE_YEARS job requirement, so an evidence row can reference it; null if none. */
            Long experienceYearsRequirementId,
            /** levelOrdinal required by the job, or 0 if the job requirements didn't clearly state one. */
            int requiredEducationLevelOrdinal,
            ResumeProfile resume) {
    }

    // ---------- Output ----------

    public record ScoreComponentResult(
            String category, double maxPoints, double earnedPoints, String explanation) {
    }

    public record EvidenceResult(
            Long requirementId, MatchType matchType, EvidenceStrength strength,
            String resumeRefType, Long resumeRefId, String explanationText, Double confidence) {
    }

    public record ScoringResult(
            int overallScore,
            String recommendation,
            String recommendationReason,
            List<ScoreComponentResult> components,
            List<EvidenceResult> evidence) {
    }
}
