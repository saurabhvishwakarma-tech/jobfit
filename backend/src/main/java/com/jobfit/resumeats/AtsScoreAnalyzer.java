package com.jobfit.resumeats;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.jobfit.resumeats.AtsModels.*;

/**
 * Deterministic ATS-parseability scoring - a separate lens from Resume
 * Quality (which grades writing strength: weak verbs, missing
 * quantification, etc). This grades how reliably an Applicant Tracking
 * System is likely to correctly parse this resume's *structure* - can it
 * find contact info, recognize standard sections, extract dates - largely
 * independent of how well-written the bullets themselves are. Pure
 * function, no I/O, no AI, same discipline as resumequality's analyzer.
 */
@Component
public class AtsScoreAnalyzer {

    static final int MIN_WORD_COUNT = 150;
    static final int MAX_WORD_COUNT = 1200;
    static final double MAX_HEALTHY_SYMBOL_RATIO = 0.04;
    static final double MIN_DATE_COVERAGE = 0.6;

    static final int FAIL_PENALTY = 15;
    static final int WARN_PENALTY = 7;

    public AtsResult analyze(AtsInput input) {
        List<AtsCheck> checks = new ArrayList<>();

        if (input.hasEmail()) {
            checks.add(new AtsCheck("Email address", AtsCheckStatus.PASS,
                    "Found and parseable - this is how most ATS platforms identify you."));
        } else {
            checks.add(new AtsCheck("Email address", AtsCheckStatus.FAIL,
                    "No email address was detected. Most ATS platforms and recruiters rely on this to reach you."));
        }

        if (input.hasPhone()) {
            checks.add(new AtsCheck("Phone number", AtsCheckStatus.PASS, "Found and parseable."));
        } else {
            checks.add(new AtsCheck("Phone number", AtsCheckStatus.WARN,
                    "No phone number was detected - some systems flag applications without one as incomplete."));
        }

        if (input.experienceCount() == 0) {
            checks.add(new AtsCheck("Work experience section", AtsCheckStatus.FAIL,
                    "No work experience entries were recognized. ATS platforms typically require a clearly "
                            + "labelled experience section to parse a candidate's history at all."));
        } else {
            checks.add(new AtsCheck("Work experience section", AtsCheckStatus.PASS,
                    "Recognized " + input.experienceCount() + " role(s) under a standard experience section."));
        }

        if (input.educationCount() == 0) {
            checks.add(new AtsCheck("Education section", AtsCheckStatus.WARN,
                    "No education entries were recognized. If you have one, make sure it sits under a clearly "
                            + "labelled \"Education\" heading."));
        } else {
            checks.add(new AtsCheck("Education section", AtsCheckStatus.PASS,
                    "Recognized " + input.educationCount() + " education entr"
                            + (input.educationCount() == 1 ? "y" : "ies") + "."));
        }

        if (input.skillCount() == 0) {
            checks.add(new AtsCheck("Skills section", AtsCheckStatus.WARN,
                    "No standalone skills were recognized. Most ATS platforms keyword-match against a "
                            + "dedicated Skills section - without one, relevant keywords may go uncounted."));
        } else {
            checks.add(new AtsCheck("Skills section", AtsCheckStatus.PASS,
                    "Recognized " + input.skillCount() + " skill(s) in a parseable list."));
        }

        if (input.wordCount() < MIN_WORD_COUNT) {
            checks.add(new AtsCheck("Resume length", AtsCheckStatus.WARN,
                    "This resume is quite short (" + input.wordCount() + " words) - ATS platforms and "
                            + "recruiters may read this as too thin on detail."));
        } else if (input.wordCount() > MAX_WORD_COUNT) {
            checks.add(new AtsCheck("Resume length", AtsCheckStatus.WARN,
                    "This resume is quite long (" + input.wordCount() + " words) - some ATS text fields "
                            + "truncate very long content."));
        } else {
            checks.add(new AtsCheck("Resume length", AtsCheckStatus.PASS,
                    "A healthy length for parsing (" + input.wordCount() + " words)."));
        }

        if (!input.experienceDates().isEmpty()) {
            long withDates = input.experienceDates().stream().filter(ExperienceDateInput::hasStartDate).count();
            double coverage = (double) withDates / input.experienceDates().size();
            if (coverage < MIN_DATE_COVERAGE) {
                checks.add(new AtsCheck("Date formatting", AtsCheckStatus.WARN,
                        "Only " + withDates + " of " + input.experienceDates().size() + " roles had a date we "
                                + "could recognize - non-standard date formats (e.g. \"Jan '22\") or dates "
                                + "placed inside tables/columns can also trip up ATS parsers."));
            } else {
                checks.add(new AtsCheck("Date formatting", AtsCheckStatus.PASS,
                        "Dates on your roles are in a standard, recognizable format."));
            }
        }

        if (input.nonStandardCharRatio() > MAX_HEALTHY_SYMBOL_RATIO) {
            checks.add(new AtsCheck("Text extraction", AtsCheckStatus.WARN,
                    "This PDF contains an unusually high proportion of special characters once extracted as "
                            + "plain text - this often means tables, multi-column layouts, icons, or text "
                            + "boxes, which many ATS parsers read out of order or drop entirely. A single-"
                            + "column, text-based layout parses most reliably."));
        } else {
            checks.add(new AtsCheck("Text extraction", AtsCheckStatus.PASS,
                    "Text extracted cleanly - no signs of tables, columns, or graphics interfering with parsing."));
        }

        checks.add(new AtsCheck("File format", AtsCheckStatus.PASS,
                "PDF is the most broadly supported format across ATS platforms."));

        int score = 100;
        for (AtsCheck check : checks) {
            if (check.status() == AtsCheckStatus.FAIL) score -= FAIL_PENALTY;
            else if (check.status() == AtsCheckStatus.WARN) score -= WARN_PENALTY;
        }
        score = Math.max(0, Math.min(100, score));

        return new AtsResult(score, checks);
    }
}
