package com.jobfit.jobparsing;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jobfit.jobparsing.JobExtractionModels.*;

/**
 * Rule-based job description structuring: regex + section-heading
 * heuristics only, no AI calls - the same "deterministic first" philosophy
 * as DeterministicResumeExtractor. Job postings vary far more in structure
 * than resumes do, so this is intentionally conservative: it only extracts
 * requirements from sections it can clearly identify, rather than guessing
 * at unstructured prose. Whatever it misses is a legitimate candidate for
 * AI-assisted enhancement later (see AiClient) - it is never invented here.
 */
@Component
public class DeterministicJobExtractor {

    private static final Map<String, Bucket> SECTION_HEADERS = buildHeaderMap();

    private static Map<String, Bucket> buildHeaderMap() {
        Map<String, Bucket> map = new HashMap<>();
        for (String h : List.of("requirements", "required skills", "required qualifications",
                "must have", "must-haves", "minimum qualifications", "qualifications",
                "what we're looking for", "what you'll need")) {
            map.put(h, Bucket.REQUIRED);
        }
        for (String h : List.of("preferred", "preferred skills", "preferred qualifications",
                "nice to have", "nice-to-haves", "bonus points", "bonus", "desirable", "pluses")) {
            map.put(h, Bucket.PREFERRED);
        }
        for (String h : List.of("responsibilities", "key responsibilities", "what you'll do",
                "duties", "the role", "role overview", "about the role", "your role")) {
            map.put(h, Bucket.RESPONSIBILITY);
        }
        for (String h : List.of("education", "education requirements")) {
            map.put(h, Bucket.EDUCATION);
        }
        return map;
    }

    private static final Pattern EDUCATION_KEYWORD_PATTERN = Pattern.compile(
            "bachelor|master|phd|ph\\.d|doctorate|\\bbsc\\b|\\bmsc\\b|\\bba\\b|\\bma\\b|degree in",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXPERIENCE_YEARS_PATTERN = Pattern.compile(
            "(\\d{1,2})\\+?\\s*(?:years?|yrs?)\\s*(?:of\\s+)?experience", Pattern.CASE_INSENSITIVE);

    public ExtractionResult extract(String rawText) {
        String normalized = rawText.replace("\r\n", "\n").replace("\r", "\n");
        List<String> lines = Arrays.asList(normalized.split("\n"));

        Map<Bucket, List<String>> sectionText = splitIntoSections(lines);

        List<RequirementLine> result = new ArrayList<>();
        for (Map.Entry<Bucket, List<String>> entry : sectionText.entrySet()) {
            for (String rawLine : entry.getValue()) {
                String line = stripBullet(rawLine);
                if (line.length() < 3) continue;

                Bucket bucket = entry.getKey();
                if (bucket != Bucket.EDUCATION && EDUCATION_KEYWORD_PATTERN.matcher(line).find()) {
                    bucket = Bucket.EDUCATION;
                }
                result.add(new RequirementLine(bucket, line));
            }
        }

        String experienceSnippet = findExperienceYearsSnippet(lines);

        return new ExtractionResult(result, experienceSnippet);
    }

    private Map<Bucket, List<String>> splitIntoSections(List<String> lines) {
        Map<Bucket, List<String>> result = new EnumMap<>(Bucket.class);
        Bucket currentBucket = null;
        List<String> currentLines = null;

        for (String rawLine : lines) {
            String key = rawLine.strip().toLowerCase(Locale.ROOT).replaceAll("[:*]+$", "");
            Bucket matched = SECTION_HEADERS.get(key);
            boolean looksLikeHeader = matched != null && rawLine.strip().length() < 60;

            if (looksLikeHeader) {
                currentBucket = matched;
                currentLines = result.computeIfAbsent(currentBucket, b -> new ArrayList<>());
                continue;
            }
            if (currentLines != null && !rawLine.isBlank()) {
                currentLines.add(rawLine);
            }
        }
        return result;
    }

    private String findExperienceYearsSnippet(List<String> lines) {
        for (String line : lines) {
            Matcher m = EXPERIENCE_YEARS_PATTERN.matcher(line);
            if (m.find()) {
                return line.strip();
            }
        }
        return null;
    }

    private String stripBullet(String line) {
        return line.strip().replaceAll("^[•‣▪●\\-*]+\\s*", "");
    }
}
