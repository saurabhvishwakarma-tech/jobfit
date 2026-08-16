package com.jobfit.scoring;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic text-similarity fallback used where no embeddings/LLM are
 * configured (see docs/JobFit_Design_v1.md, "Where AI Should/Should Not Be
 * Used" - responsibility-bullet matching is AI-assisted *when available*,
 * but the application must produce a real, reproducible signal without any
 * provider configured). Jaccard similarity over lowercased, stopword-
 * filtered word sets. Crude compared to semantic embeddings, but fully
 * explainable: the exact overlapping words could be shown in the UI if
 * desired, which a black-box embedding score cannot offer.
 */
public final class LexicalSimilarity {

    private LexicalSimilarity() {
    }

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "a", "an", "of", "in", "on", "with", "to", "for", "is", "are", "was", "were",
            "be", "by", "as", "at", "or", "that", "this", "from", "using", "use", "our", "your", "you",
            "we", "will", "have", "has", "had", "into", "across", "about", "including", "such", "other",
            "team", "teams", "work", "working", "role", "years", "year", "experience", "strong", "skills",
            "ability", "excellent", "good", "knowledge", "understanding", "etc"
    );

    public static double jaccard(String a, String b) {
        Set<String> tokensA = tokenize(a);
        Set<String> tokensB = tokenize(b);
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);
        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);
        return (double) intersection.size() / union.size();
    }

    /** Highest similarity between `text` and any candidate; 0.0 if candidates is empty. */
    public static double maxSimilarity(String text, Iterable<String> candidates) {
        double best = 0.0;
        for (String candidate : candidates) {
            best = Math.max(best, jaccard(text, candidate));
        }
        return best;
    }

    private static Set<String> tokenize(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+")) {
            if (token.length() > 1 && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
