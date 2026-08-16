package com.jobfit.resumequality;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.jobfit.resumequality.QualityModels.*;

/**
 * The deterministic core of Resume Quality Analysis: a rule-based writing
 * linter, independent of any specific job (see docs/JobFit_Design_v1.md,
 * "Resume Quality Analysis as a standalone feature" - weak verbs, missing
 * quantification, vague adjectives, ATS-parseability issues). Pure
 * function, no I/O, no AI - every issue is traceable to a specific rule
 * and, wherever possible, a specific bullet.
 *
 * Deliberately has no other Spring dependencies - it's @Component only so
 * it can be injected, exactly like scoring.ScoringEngine - so it can be
 * constructed and tested with `new ResumeQualityAnalyzer()`.
 */
@Component
public class ResumeQualityAnalyzer {

    static final int MIN_TOTAL_HIGHLIGHTS = 3;
    static final int MIN_LEN_FOR_QUANT_CHECK = 30;
    static final int MIN_BULLET_LEN = 15;
    static final int MAX_BULLET_LEN = 220;
    static final double OVERUSE_RATIO = 0.3;
    static final int MIN_BULLETS_FOR_VARIETY_CHECK = 4;

    static final int HIGH_PENALTY = 10;
    static final int MEDIUM_PENALTY = 5;
    static final int LOW_PENALTY = 2;

    private static final Pattern QUANT_PATTERN = Pattern.compile("[0-9%$€£]");
    private static final Pattern FIRST_PERSON_PATTERN = Pattern.compile("\\b(i|my|me)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_WORD = Pattern.compile("^[A-Za-z]+");

    private static final List<String> WEAK_PHRASES = List.of(
            "responsible for", "duties included", "worked on", "helped with",
            "assisted with", "involved in", "in charge of", "tasked with");

    private static final List<String> BUZZWORDS = List.of(
            "team player", "hard-working", "hard working", "detail-oriented", "detail oriented",
            "results-driven", "results driven", "self-starter", "self starter", "go-getter", "go getter",
            "think outside the box", "outside the box", "synergy", "passionate about");

    public QualityResult analyze(QualityInput input) {
        List<QualityIssue> issues = new ArrayList<>();

        checkContactInfo(input, issues);
        checkStructure(input, issues);
        for (BulletInput bullet : input.allHighlights()) {
            checkBullet(bullet, issues);
        }
        checkVerbVariety(input.allHighlights(), issues);

        int score = 100;
        for (QualityIssue issue : issues) {
            score -= penaltyFor(issue.severity());
        }
        score = Math.max(0, Math.min(100, score));

        return new QualityResult(score, issues);
    }

    // ---------- Resume-level structural checks ----------

    private void checkContactInfo(QualityInput input, List<QualityIssue> issues) {
        if (!input.hasEmail()) {
            issues.add(new QualityIssue("Contact Info", IssueSeverity.HIGH,
                    "No email address found - most application systems and recruiters need this to reach you.",
                    null, null));
        }
        if (!input.hasPhone()) {
            issues.add(new QualityIssue("Contact Info", IssueSeverity.MEDIUM,
                    "No phone number found - some recruiters still prefer to call.", null, null));
        }
    }

    private void checkStructure(QualityInput input, List<QualityIssue> issues) {
        if (input.experiences().isEmpty()) {
            issues.add(new QualityIssue("Structure", IssueSeverity.HIGH,
                    "No work experience was detected on this resume.", null, null));
        }
        if (input.skillCount() == 0) {
            issues.add(new QualityIssue("Structure", IssueSeverity.MEDIUM,
                    "No skills were detected - add a clear Skills section so both ATS systems and this tool can find them.",
                    null, null));
        }
        if (input.allHighlights().size() < MIN_TOTAL_HIGHLIGHTS) {
            issues.add(new QualityIssue("Structure", IssueSeverity.MEDIUM,
                    "Your resume has only " + input.allHighlights().size() + " bullet point(s) in total - "
                            + "consider adding more detail about what you actually did in each role.",
                    null, null));
        }
        for (ExperienceInput exp : input.experiences()) {
            if (exp.highlightCount() == 0) {
                issues.add(new QualityIssue("Structure", IssueSeverity.MEDIUM,
                        "\"" + exp.jobTitle() + "\" has no bullet points describing what you did in this role.",
                        "EXPERIENCE", exp.id()));
            }
        }
    }

    // ---------- Per-bullet checks ----------

    private void checkBullet(BulletInput bullet, List<QualityIssue> issues) {
        String text = bullet.text() == null ? "" : bullet.text();

        if (text.length() >= MIN_LEN_FOR_QUANT_CHECK && !QUANT_PATTERN.matcher(text).find()) {
            issues.add(new QualityIssue("Impact", IssueSeverity.LOW,
                    "This bullet doesn't include a number, percentage, or other measurable outcome - "
                            + "quantified impact is more convincing than a description of duties.",
                    "EXPERIENCE_HIGHLIGHT", bullet.id()));
        }

        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String phrase : WEAK_PHRASES) {
            if (lower.contains(phrase)) {
                issues.add(new QualityIssue("Phrasing", IssueSeverity.MEDIUM,
                        "\"" + phrase + "\" is passive/vague - lead with a strong action verb describing what you did instead.",
                        "EXPERIENCE_HIGHLIGHT", bullet.id()));
                break; // one flag per bullet for this rule is enough signal
            }
        }

        for (String buzzword : BUZZWORDS) {
            if (lower.contains(buzzword)) {
                issues.add(new QualityIssue("Phrasing", IssueSeverity.LOW,
                        "\"" + buzzword + "\" is a generic buzzword - replace it with a concrete example instead.",
                        "EXPERIENCE_HIGHLIGHT", bullet.id()));
                break;
            }
        }

        if (FIRST_PERSON_PATTERN.matcher(text).find()) {
            issues.add(new QualityIssue("Phrasing", IssueSeverity.LOW,
                    "Resume bullets conventionally drop the first-person pronoun (\"I\", \"my\") - "
                            + "start directly with the action verb.",
                    "EXPERIENCE_HIGHLIGHT", bullet.id()));
        }

        if (text.length() > MAX_BULLET_LEN) {
            issues.add(new QualityIssue("Readability", IssueSeverity.LOW,
                    "This bullet is long (" + text.length() + " characters) - consider splitting it so it's easier to scan.",
                    "EXPERIENCE_HIGHLIGHT", bullet.id()));
        } else if (text.length() < MIN_BULLET_LEN) {
            issues.add(new QualityIssue("Readability", IssueSeverity.LOW,
                    "This bullet is very short - it may not convey enough detail to be convincing.",
                    "EXPERIENCE_HIGHLIGHT", bullet.id()));
        }
    }

    // ---------- Cross-bullet check ----------

    private void checkVerbVariety(List<BulletInput> highlights, List<QualityIssue> issues) {
        if (highlights.size() < MIN_BULLETS_FOR_VARIETY_CHECK) {
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BulletInput bullet : highlights) {
            String word = leadingWord(bullet.text());
            if (word == null) continue;
            counts.merge(word, 1, Integer::sum);
        }
        int total = highlights.size();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            double ratio = (double) entry.getValue() / total;
            if (ratio > OVERUSE_RATIO) {
                issues.add(new QualityIssue("Variety", IssueSeverity.MEDIUM,
                        "You start " + entry.getValue() + " of your " + total + " bullets with \"" + entry.getKey()
                                + "\" - vary your action verbs so each bullet stands out.",
                        null, null));
                break; // report the single most-overused word, not every word above the threshold
            }
        }
    }

    private String leadingWord(String text) {
        if (text == null) return null;
        var m = LEADING_WORD.matcher(text.strip());
        if (!m.find()) return null;
        return m.group().toLowerCase(java.util.Locale.ROOT);
    }

    private int penaltyFor(IssueSeverity severity) {
        return switch (severity) {
            case HIGH -> HIGH_PENALTY;
            case MEDIUM -> MEDIUM_PENALTY;
            case LOW -> LOW_PENALTY;
        };
    }
}
