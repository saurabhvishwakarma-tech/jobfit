package com.jobfit.matching;

import java.util.regex.Pattern;

/**
 * Best-effort, deterministic mapping from free-text degree descriptions to
 * an ordinal level (0=unspecified, 1=Bachelor's, 2=Master's, 3=Doctorate),
 * used on both sides: a resume's Education.degree field and a job's
 * EDUCATION requirement text. Same conservative philosophy as the rest of
 * the deterministic pipeline - if it can't tell, it says so (0) rather than
 * guessing.
 */
final class DegreeLevelClassifier {

    private DegreeLevelClassifier() {
    }

    private static final Pattern DOCTORATE = Pattern.compile(
            "phd|ph\\.d|doctorate|doctoral", Pattern.CASE_INSENSITIVE);
    private static final Pattern MASTERS = Pattern.compile(
            "master|\\bmsc\\b|\\bm\\.sc\\b|\\bma\\b|\\bmba\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACHELORS = Pattern.compile(
            "bachelor|\\bbsc\\b|\\bb\\.sc\\b|\\bba\\b|\\bbeng\\b|undergraduate degree", Pattern.CASE_INSENSITIVE);

    static int ordinalFor(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        if (DOCTORATE.matcher(text).find()) return 3;
        if (MASTERS.matcher(text).find()) return 2;
        if (BACHELORS.matcher(text).find()) return 1;
        return 0;
    }
}
