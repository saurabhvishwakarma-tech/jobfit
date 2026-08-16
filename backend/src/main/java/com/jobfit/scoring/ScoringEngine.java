package com.jobfit.scoring;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.jobfit.scoring.ScoringModels.*;

/**
 * The deterministic core of JobFit. Pure function: same input always
 * produces the same output, no I/O, no AI calls. Every point awarded is
 * traceable to a specific rule below - see docs/JobFit_Design_v1.md,
 * "Matching & Scoring Algorithm" for the weight rationale.
 *
 * Weights (of 100): Required skills 35, Preferred skills 10,
 * Experience 20, Responsibilities/Domain 20, Education 10, Soft skills 5.
 *
 * Deliberately has no other Spring dependencies (no repositories, no
 * @Transactional, no I/O) - it's @Component only so it can be injected,
 * not because it needs the container for anything. This is what makes
 * ScoringEngineTest able to instantiate it with `new ScoringEngine()` and
 * test it with zero Spring context.
 */
@Component
public class ScoringEngine {

    static final double REQUIRED_SKILLS_MAX = 35.0;
    static final double PREFERRED_SKILLS_MAX = 10.0;
    static final double EXPERIENCE_MAX = 20.0;
    static final double RESPONSIBILITIES_MAX = 20.0;
    static final double EDUCATION_MAX = 10.0;
    static final double SOFT_SKILLS_MAX = 5.0;

    /** Credit given for an INFERRED skill match relative to an EXPLICIT one. */
    static final double INFERRED_SKILL_CREDIT = 0.6;

    static final double RESPONSIBILITY_STRONG_THRESHOLD = 0.5;
    static final double RESPONSIBILITY_PARTIAL_THRESHOLD = 0.2;

    public ScoringResult score(ScoringInput input) {
        List<ItemResult> requiredItems = matchSkillBased(input.requiredSkills(), input.resume());
        List<ItemResult> preferredItems = matchSkillBased(input.preferredSkills(), input.resume());
        List<ItemResult> domainItems = matchSkillBased(input.domainRequirements(), input.resume());
        List<ItemResult> softSkillItems = matchSkillBased(input.softSkillRequirements(), input.resume());
        List<ItemResult> responsibilityItems = matchResponsibilities(input.responsibilities(), input.resume());
        List<ItemResult> educationItems =
                matchEducation(input.educationRequirements(), input.resume(), input.requiredEducationLevelOrdinal());
        ExperienceResult experienceResult = scoreExperience(input);

        ScoreComponentResult requiredComponent = summarize("Required skills", REQUIRED_SKILLS_MAX, requiredItems,
                "No required skills were clearly identified in this job description.");
        ScoreComponentResult preferredComponent = summarize("Preferred skills", PREFERRED_SKILLS_MAX, preferredItems,
                "No preferred skills were clearly identified in this job description.");

        List<ItemResult> respAndDomain = new ArrayList<>(responsibilityItems);
        respAndDomain.addAll(domainItems);
        ScoreComponentResult responsibilitiesComponent = summarize(
                "Responsibilities & domain", RESPONSIBILITIES_MAX, respAndDomain,
                "No responsibilities were clearly identified in this job description.");

        ScoreComponentResult educationComponent = summarize("Education", EDUCATION_MAX, educationItems,
                "No education requirement was clearly identified in this job description.");
        ScoreComponentResult softSkillsComponent = summarize("Soft skills", SOFT_SKILLS_MAX, softSkillItems,
                "No soft skills were clearly identified in this job description.");

        List<ScoreComponentResult> components = List.of(
                requiredComponent, preferredComponent, experienceResult.component(),
                responsibilitiesComponent, educationComponent, softSkillsComponent);

        int overallScore = (int) Math.round(components.stream().mapToDouble(ScoreComponentResult::earnedPoints).sum());

        List<EvidenceResult> evidence = new ArrayList<>();
        requiredItems.forEach(i -> evidence.add(i.toEvidence()));
        preferredItems.forEach(i -> evidence.add(i.toEvidence()));
        domainItems.forEach(i -> evidence.add(i.toEvidence()));
        softSkillItems.forEach(i -> evidence.add(i.toEvidence()));
        responsibilityItems.forEach(i -> evidence.add(i.toEvidence()));
        educationItems.forEach(i -> evidence.add(i.toEvidence()));
        if (experienceResult.evidence() != null) {
            evidence.add(experienceResult.evidence());
        }

        long missingRequired = requiredItems.stream().filter(i -> i.strength == EvidenceStrength.MISSING).count();
        long matchedRequired = requiredItems.size() - missingRequired;

        String recommendation = recommend(overallScore, missingRequired > 0);
        String reason = explainRecommendation(recommendation, overallScore, matchedRequired,
                requiredItems.size(), missingRequired);

        return new ScoringResult(overallScore, recommendation, reason, components, evidence);
    }

    // ---------- Skill-based matching (required / preferred / domain / soft skills) ----------

    private List<ItemResult> matchSkillBased(List<RequirementInput> requirements, ResumeProfile resume) {
        List<ItemResult> results = new ArrayList<>();
        for (RequirementInput req : requirements) {
            results.add(matchOneSkillRequirement(req, resume));
        }
        return results;
    }

    private ItemResult matchOneSkillRequirement(RequirementInput req, ResumeProfile resume) {
        if (req.skillId() != null) {
            ResumeSkillInput found = resume.skills().stream()
                    .filter(s -> s.skillId().equals(req.skillId()))
                    .findFirst().orElse(null);
            if (found != null && found.source() == ResumeSkillSource.EXPLICIT) {
                String ref = found.evidenceHighlightId() != null ? "EXPERIENCE_HIGHLIGHT" : "SKILL";
                Long refId = found.evidenceHighlightId() != null ? found.evidenceHighlightId() : found.skillId();
                return new ItemResult(req.requirementId(), 1.0, MatchType.EXPLICIT, EvidenceStrength.STRONG,
                        ref, refId, "Your resume explicitly lists \"" + found.name() + "\".", 1.0);
            }
            if (found != null && found.source() == ResumeSkillSource.INFERRED) {
                String ref = found.evidenceHighlightId() != null ? "EXPERIENCE_HIGHLIGHT" : "SKILL";
                Long refId = found.evidenceHighlightId() != null ? found.evidenceHighlightId() : found.skillId();
                return new ItemResult(req.requirementId(), INFERRED_SKILL_CREDIT, MatchType.INFERRED,
                        EvidenceStrength.PARTIAL, ref, refId,
                        "\"" + found.name() + "\" was inferred from related experience, not stated explicitly.", 0.6);
            }
            return new ItemResult(req.requirementId(), 0.0, MatchType.ABSENT, EvidenceStrength.MISSING,
                    null, null, "No evidence of this skill was found in your resume.", null);
        }

        // No taxonomy skill could be resolved from this requirement's text - fall back to
        // lexical overlap against resume highlights and skill names so an ungraded
        // requirement doesn't just silently disappear from scoring.
        double bestSim = 0.0;
        Long bestHighlightId = null;
        for (HighlightInput h : resume.highlights()) {
            double sim = LexicalSimilarity.jaccard(req.text(), h.text());
            if (sim > bestSim) {
                bestSim = sim;
                bestHighlightId = h.id();
            }
        }
        if (bestSim >= 0.34) {
            EvidenceStrength strength = bestSim >= 0.5 ? EvidenceStrength.STRONG : EvidenceStrength.PARTIAL;
            return new ItemResult(req.requirementId(), Math.min(1.0, bestSim), MatchType.INFERRED, strength,
                    "EXPERIENCE_HIGHLIGHT", bestHighlightId,
                    "Related wording found in your resume, though this requirement didn't map to a known skill.",
                    bestSim);
        }
        return new ItemResult(req.requirementId(), 0.0, MatchType.ABSENT, EvidenceStrength.MISSING,
                null, null, "No related evidence was found in your resume.", null);
    }

    // ---------- Responsibilities (lexical similarity) ----------

    private List<ItemResult> matchResponsibilities(List<RequirementInput> requirements, ResumeProfile resume) {
        List<ItemResult> results = new ArrayList<>();
        for (RequirementInput req : requirements) {
            double bestSim = 0.0;
            Long bestHighlightId = null;
            for (HighlightInput h : resume.highlights()) {
                double sim = LexicalSimilarity.jaccard(req.text(), h.text());
                if (sim > bestSim) {
                    bestSim = sim;
                    bestHighlightId = h.id();
                }
            }
            if (bestSim >= RESPONSIBILITY_PARTIAL_THRESHOLD) {
                EvidenceStrength strength = bestSim >= RESPONSIBILITY_STRONG_THRESHOLD
                        ? EvidenceStrength.STRONG : EvidenceStrength.PARTIAL;
                results.add(new ItemResult(req.requirementId(), Math.min(1.0, bestSim), MatchType.INFERRED, strength,
                        "EXPERIENCE_HIGHLIGHT", bestHighlightId,
                        "Similar wording found in your resume (similarity " + Math.round(bestSim * 100) + "%).",
                        bestSim));
            } else {
                results.add(new ItemResult(req.requirementId(), 0.0, MatchType.ABSENT, EvidenceStrength.MISSING,
                        null, null, "No similar experience found in your resume.", null));
            }
        }
        return results;
    }

    // ---------- Education ----------

    private List<ItemResult> matchEducation(List<RequirementInput> requirements, ResumeProfile resume,
                                             int requiredLevel) {
        if (requirements.isEmpty()) {
            return List.of();
        }
        int bestResumeLevel = resume.education().stream()
                .mapToInt(EducationInput::levelOrdinal).max().orElse(0);

        List<ItemResult> results = new ArrayList<>();
        for (RequirementInput req : requirements) {
            if (requiredLevel == 0 || bestResumeLevel >= requiredLevel) {
                Long refId = resume.education().stream()
                        .filter(e -> e.levelOrdinal() == bestResumeLevel).map(EducationInput::id)
                        .findFirst().orElse(null);
                results.add(new ItemResult(req.requirementId(), 1.0, MatchType.EXPLICIT, EvidenceStrength.STRONG,
                        refId == null ? null : "EDUCATION", refId,
                        "Your education meets or exceeds this requirement.", 1.0));
            } else if (bestResumeLevel > 0) {
                results.add(new ItemResult(req.requirementId(), 0.5, MatchType.INFERRED, EvidenceStrength.PARTIAL,
                        null, null, "Your resume lists education, but not at the level this role asks for.", 0.5));
            } else {
                results.add(new ItemResult(req.requirementId(), 0.0, MatchType.ABSENT, EvidenceStrength.MISSING,
                        null, null, "No education matching this requirement was found in your resume.", null));
            }
        }
        return results;
    }

    // ---------- Experience (years + title relevance) ----------

    private record ExperienceResult(ScoreComponentResult component, EvidenceResult evidence) {
    }

    private ExperienceResult scoreExperience(ScoringInput input) {
        double titleRelevance = LexicalSimilarity.maxSimilarity(
                input.jobTitle() == null ? "" : input.jobTitle(), input.resume().experienceJobTitles());

        Integer requiredYears = input.requiredYearsExperience();
        double yearsRatio;
        EvidenceResult evidence = null;

        if (requiredYears == null || requiredYears <= 0) {
            yearsRatio = 1.0;
        } else {
            yearsRatio = Math.min(1.0, input.resume().totalYearsExperience() / requiredYears);
            EvidenceStrength strength = yearsRatio >= 0.99 ? EvidenceStrength.STRONG
                    : yearsRatio >= 0.5 ? EvidenceStrength.PARTIAL : EvidenceStrength.MISSING;
            MatchType matchType = yearsRatio >= 0.99 ? MatchType.EXPLICIT
                    : yearsRatio > 0 ? MatchType.INFERRED : MatchType.ABSENT;
            String explanation = String.format(
                    "Resume shows approximately %.1f years of relevant experience versus %d required.",
                    input.resume().totalYearsExperience(), requiredYears);
            if (input.experienceYearsRequirementId() != null) {
                evidence = new EvidenceResult(input.experienceYearsRequirementId(), matchType, strength,
                        null, null, explanation, yearsRatio);
            }
        }

        double fraction = 0.6 * yearsRatio + 0.4 * titleRelevance;
        double earned = round2(fraction * EXPERIENCE_MAX);
        String explanation = requiredYears != null && requiredYears > 0
                ? String.format("%.1f/%d years of experience; title relevance %d%%.",
                        input.resume().totalYearsExperience(), requiredYears, Math.round(titleRelevance * 100))
                : String.format("No specific years requirement stated; title relevance %d%%.",
                        Math.round(titleRelevance * 100));

        return new ExperienceResult(
                new ScoreComponentResult("Experience", EXPERIENCE_MAX, earned, explanation), evidence);
    }

    // ---------- Recommendation ----------

    private String recommend(int overallScore, boolean missingRequiredSkill) {
        if (overallScore >= 80) {
            return missingRequiredSkill ? "STRETCH_APPLICATION" : "STRONG_MATCH";
        } else if (overallScore >= 60) {
            return "REASONABLE_MATCH";
        } else if (overallScore >= 40) {
            return "STRETCH_APPLICATION";
        } else {
            return "POOR_MATCH";
        }
    }

    private String explainRecommendation(String recommendation, int overallScore, long matchedRequired,
                                          int totalRequired, long missingRequired) {
        StringBuilder sb = new StringBuilder();
        sb.append("Job Fit: ").append(overallScore).append("%. ");
        if (totalRequired > 0) {
            sb.append("You meet ").append(matchedRequired).append("/").append(totalRequired)
                    .append(" required skills. ");
            if (missingRequired > 0) {
                sb.append(missingRequired).append(" required skill")
                        .append(missingRequired == 1 ? " is" : "s are")
                        .append(" missing - see the Required skills section below. ");
            }
        }
        switch (recommendation) {
            case "STRONG_MATCH" -> sb.append("This is a strong match - go ahead and apply.");
            case "REASONABLE_MATCH" -> sb.append("This is a reasonable match worth applying to.");
            case "STRETCH_APPLICATION" -> sb.append("This is a stretch application - possible, but you're missing "
                    + "some ground the job asks for.");
            case "POOR_MATCH" -> sb.append("This role doesn't line up well with your resume as it stands.");
            default -> {
            }
        }
        return sb.toString();
    }

    // ---------- Shared summarization ----------

    private ScoreComponentResult summarize(String category, double maxPoints, List<ItemResult> items,
                                            String emptyExplanation) {
        if (items.isEmpty()) {
            return new ScoreComponentResult(category, maxPoints, round2(maxPoints), emptyExplanation);
        }
        double avgFraction = items.stream().mapToDouble(i -> i.fraction).average().orElse(0.0);
        double earned = round2(avgFraction * maxPoints);
        long strong = items.stream().filter(i -> i.strength == EvidenceStrength.STRONG).count();
        long partial = items.stream().filter(i -> i.strength == EvidenceStrength.PARTIAL).count();
        long missing = items.stream().filter(i -> i.strength == EvidenceStrength.MISSING).count();
        String explanation = String.format("%d strong, %d partial, %d missing (out of %d).",
                strong, partial, missing, items.size());
        return new ScoreComponentResult(category, maxPoints, earned, explanation);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record ItemResult(Long requirementId, double fraction, MatchType matchType, EvidenceStrength strength,
                               String resumeRefType, Long resumeRefId, String explanationText, Double confidence) {
        EvidenceResult toEvidence() {
            return new EvidenceResult(requirementId, matchType, strength, resumeRefType, resumeRefId,
                    explanationText, confidence);
        }
    }
}
