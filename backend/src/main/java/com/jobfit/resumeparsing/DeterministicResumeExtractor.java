package com.jobfit.resumeparsing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jobfit.resumeparsing.ExtractionModels.*;

/**
 * Rule-based resume structuring: regex + heuristics only, no AI calls. This
 * is deliberately the primary extraction path (see docs/JobFit_Design_v1.md,
 * "AI Is Not The Product") - it is not perfect on every resume layout, and
 * it isn't meant to be; the ResumeController PATCH endpoint and the
 * frontend edit screen exist specifically so a user reviews and corrects
 * whatever this gets wrong before it's used for anything downstream.
 */
@Component
public class DeterministicResumeExtractor {

    private static final Logger log = LoggerFactory.getLogger(DeterministicResumeExtractor.class);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?\\d[\\d .()-]{7,}\\d)");
    private static final Pattern LINKEDIN_PATTERN =
            Pattern.compile("(https?://)?(www\\.)?linkedin\\.com/[\\w\\-/]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_PATTERN =
            Pattern.compile("(https?://)?(www\\.)?github\\.com/[\\w\\-/]+", Pattern.CASE_INSENSITIVE);

    private static final String MONTH = "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{4}";
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(?<start>" + MONTH + "|\\d{4})\\s*(?:-|–|—|to)\\s*(?<end>" + MONTH + "|\\d{4}|Present|Current)",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, SectionType> SECTION_HEADERS = buildSectionHeaderMap();

    private static Map<String, SectionType> buildSectionHeaderMap() {
        Map<String, SectionType> map = new HashMap<>();
        for (String h : List.of("experience", "work experience", "professional experience",
                "employment history", "work history")) {
            map.put(h, SectionType.EXPERIENCE);
        }
        for (String h : List.of("education", "academic background")) {
            map.put(h, SectionType.EDUCATION);
        }
        for (String h : List.of("skills", "technical skills", "core competencies", "key skills")) {
            map.put(h, SectionType.SKILLS);
        }
        for (String h : List.of("certifications", "certificates", "licenses & certifications")) {
            map.put(h, SectionType.CERTIFICATIONS);
        }
        for (String h : List.of("projects", "personal projects", "key projects")) {
            map.put(h, SectionType.PROJECTS);
        }
        for (String h : List.of("summary", "profile", "objective", "about")) {
            map.put(h, SectionType.SUMMARY);
        }
        return map;
    }

    private enum SectionType { EXPERIENCE, EDUCATION, SKILLS, CERTIFICATIONS, PROJECTS, SUMMARY }

    public ExtractionResult extract(String rawText) {
        String normalized = rawText.replace("\r\n", "\n").replace("\r", "\n");
        List<String> lines = Arrays.asList(normalized.split("\n"));

        ContactInfo contactInfo = extractContactInfo(normalized, lines);
        Map<SectionType, String> sections = splitIntoSections(lines);

        List<Experience> experiences = sections.containsKey(SectionType.EXPERIENCE)
                ? extractExperiences(sections.get(SectionType.EXPERIENCE)) : List.of();
        List<Education> education = sections.containsKey(SectionType.EDUCATION)
                ? extractEducation(sections.get(SectionType.EDUCATION)) : List.of();
        List<Certification> certifications = sections.containsKey(SectionType.CERTIFICATIONS)
                ? extractCertifications(sections.get(SectionType.CERTIFICATIONS)) : List.of();
        List<Project> projects = sections.containsKey(SectionType.PROJECTS)
                ? extractProjects(sections.get(SectionType.PROJECTS)) : List.of();
        List<String> skillTerms = sections.containsKey(SectionType.SKILLS)
                ? extractSkillTerms(sections.get(SectionType.SKILLS)) : List.of();

        return new ExtractionResult(contactInfo, experiences, education, certifications, projects, skillTerms);
    }

    // ---------- Contact info ----------

    private ContactInfo extractContactInfo(String fullText, List<String> lines) {
        String email = firstMatch(EMAIL_PATTERN, fullText);
        String phone = firstMatch(PHONE_PATTERN, fullText);
        String linkedin = firstMatch(LINKEDIN_PATTERN, fullText);
        String github = firstMatch(GITHUB_PATTERN, fullText);

        // Best-effort name guess: first non-blank line that isn't itself an
        // email/phone/URL and isn't a known section header.
        String fullName = null;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;
            if (EMAIL_PATTERN.matcher(trimmed).find() || PHONE_PATTERN.matcher(trimmed).find()) continue;
            if (SECTION_HEADERS.containsKey(trimmed.toLowerCase(Locale.ROOT))) continue;
            if (trimmed.length() > 60) continue;
            fullName = trimmed;
            break;
        }

        return new ContactInfo(fullName, email, phone, null, linkedin, github, null);
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group().strip() : null;
    }

    // ---------- Section splitting ----------

    private Map<SectionType, String> splitIntoSections(List<String> lines) {
        Map<SectionType, String> result = new EnumMap<>(SectionType.class);
        int currentStart = -1;
        SectionType currentType = null;

        for (int i = 0; i < lines.size(); i++) {
            String key = lines.get(i).strip().toLowerCase(Locale.ROOT).replaceAll(":$", "");
            SectionType matched = SECTION_HEADERS.get(key);
            boolean looksLikeHeader = matched != null && lines.get(i).strip().length() < 50;
            if (looksLikeHeader) {
                if (currentType != null) {
                    result.put(currentType, String.join("\n", lines.subList(currentStart, i)));
                }
                currentType = matched;
                currentStart = i + 1;
            }
        }
        if (currentType != null && currentStart <= lines.size()) {
            result.put(currentType, String.join("\n", lines.subList(currentStart, lines.size())));
        }
        return result;
    }

    // ---------- Experience ----------

    private List<Experience> extractExperiences(String sectionText) {
        List<Experience> results = new ArrayList<>();
        for (String block : splitIntoBlocks(sectionText)) {
            try {
                Experience exp = parseExperienceBlock(block);
                if (exp != null) results.add(exp);
            } catch (RuntimeException e) {
                log.debug("Skipping unparsable experience block: {}", e.getMessage());
            }
        }
        return results;
    }

    private Experience parseExperienceBlock(String block) {
        List<String> lines = nonBlankLines(block);
        if (lines.isEmpty()) return null;

        String headerLine = lines.get(0);
        DateRangeResult dates = findDateRange(block);

        String[] parts = splitHeader(headerLine);
        String title = parts[0];
        String company = parts.length > 1 ? parts[1] : "";

        List<String> highlights = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = stripBullet(lines.get(i));
            if (line.isBlank()) continue;
            if (DATE_RANGE_PATTERN.matcher(line).find() && line.length() < 40) continue; // likely a date/location line
            highlights.add(line);
        }

        return new Experience(title, company, null,
                dates.start(), dates.end(), dates.current(), highlights);
    }

    // ---------- Education ----------

    private List<Education> extractEducation(String sectionText) {
        List<Education> results = new ArrayList<>();
        for (String block : splitIntoBlocks(sectionText)) {
            List<String> lines = nonBlankLines(block);
            if (lines.isEmpty()) continue;
            try {
                String[] parts = lines.get(0).split(",");
                String institution = parts[0].strip();
                String degree = parts.length > 1 ? parts[1].strip() : null;
                String field = parts.length > 2 ? parts[2].strip() : null;
                DateRangeResult dates = findDateRange(block);
                results.add(new Education(institution, degree, field, dates.start(), dates.end()));
            } catch (RuntimeException e) {
                log.debug("Skipping unparsable education block: {}", e.getMessage());
            }
        }
        return results;
    }

    // ---------- Certifications ----------

    private List<Certification> extractCertifications(String sectionText) {
        List<Certification> results = new ArrayList<>();
        for (String line : nonBlankLines(sectionText)) {
            String clean = stripBullet(line);
            if (clean.isBlank()) continue;
            String[] parts = clean.split("[,–-]", 2);
            String name = parts[0].strip();
            String issuer = parts.length > 1 ? parts[1].strip() : null;
            DateRangeResult dates = findDateRange(clean);
            results.add(new Certification(name, issuer, dates.start()));
        }
        return results;
    }

    // ---------- Projects ----------

    private List<Project> extractProjects(String sectionText) {
        List<Project> results = new ArrayList<>();
        for (String block : splitIntoBlocks(sectionText)) {
            List<String> lines = nonBlankLines(block);
            if (lines.isEmpty()) continue;
            String name = lines.get(0).strip();
            String technologies = null;
            StringBuilder description = new StringBuilder();
            for (int i = 1; i < lines.size(); i++) {
                String line = stripBullet(lines.get(i));
                if (line.toLowerCase(Locale.ROOT).startsWith("tech")) {
                    int colon = line.indexOf(':');
                    technologies = colon >= 0 ? line.substring(colon + 1).strip() : line;
                } else {
                    if (!description.isEmpty()) description.append(' ');
                    description.append(line);
                }
            }
            results.add(new Project(name, description.isEmpty() ? null : description.toString(), technologies));
        }
        return results;
    }

    // ---------- Skills ----------

    private List<String> extractSkillTerms(String sectionText) {
        String[] rawTerms = sectionText.split("[,\n•‣▪;|]");
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String raw : rawTerms) {
            String term = stripBullet(raw).strip();
            term = term.replaceAll("^[-:\\s]+", "").strip();
            if (term.length() >= 2 && term.length() <= 40) {
                terms.add(term);
            }
        }
        return new ArrayList<>(terms);
    }

    // ---------- Shared helpers ----------

    private List<String> splitIntoBlocks(String sectionText) {
        String[] rawBlocks = sectionText.split("\n\\s*\n");
        List<String> blocks = new ArrayList<>();
        for (String b : rawBlocks) {
            if (!b.isBlank()) blocks.add(b);
        }
        // Fallback: if the section had no blank-line separation at all, treat
        // consecutive lines starting a new block whenever a date range is
        // found, so single-spaced resumes still split into entries.
        if (blocks.size() == 1) {
            List<String> lines = nonBlankLines(blocks.get(0));
            List<String> regrouped = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int entriesStarted = 0;
            for (String line : lines) {
                boolean isNewEntryStart = DATE_RANGE_PATTERN.matcher(line).find() && entriesStarted > 0;
                if (isNewEntryStart && !current.isEmpty()) {
                    regrouped.add(current.toString());
                    current = new StringBuilder();
                }
                if (DATE_RANGE_PATTERN.matcher(line).find()) entriesStarted++;
                if (!current.isEmpty()) current.append('\n');
                current.append(line);
            }
            if (!current.isEmpty()) regrouped.add(current.toString());
            if (regrouped.size() > 1) return regrouped;
        }
        return blocks;
    }

    private List<String> nonBlankLines(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.isBlank()) out.add(line.strip());
        }
        return out;
    }

    private String stripBullet(String line) {
        return line.strip().replaceAll("^[•‣▪●\\-*]+\\s*", "");
    }

    private String[] splitHeader(String headerLine) {
        for (String delimiter : List.of(" at ", " @ ", " – ", " - ", "|")) {
            if (headerLine.contains(delimiter)) {
                String[] parts = headerLine.split(Pattern.quote(delimiter), 2);
                return new String[]{parts[0].strip(), parts[1].strip()};
            }
        }
        if (headerLine.contains(",")) {
            String[] parts = headerLine.split(",", 2);
            return new String[]{parts[0].strip(), parts[1].strip()};
        }
        return new String[]{headerLine.strip()};
    }

    private record DateRangeResult(LocalDate start, LocalDate end, boolean current) {
    }

    private DateRangeResult findDateRange(String text) {
        Matcher m = DATE_RANGE_PATTERN.matcher(text);
        if (!m.find()) {
            return new DateRangeResult(null, null, false);
        }
        LocalDate start = parseFlexibleDate(m.group("start"));
        String endRaw = m.group("end");
        boolean current = endRaw.equalsIgnoreCase("present") || endRaw.equalsIgnoreCase("current");
        LocalDate end = current ? null : parseFlexibleDate(endRaw);
        return new DateRangeResult(start, end, current);
    }

    private static final DateTimeFormatter MONTH_YEAR_FORMAT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private LocalDate parseFlexibleDate(String raw) {
        String cleaned = raw.strip().replaceAll("\\.", "");
        try {
            if (cleaned.matches("\\d{4}")) {
                return LocalDate.of(Integer.parseInt(cleaned), 1, 1);
            }
            String normalized = cleaned.substring(0, 1).toUpperCase(Locale.ENGLISH)
                    + cleaned.substring(1, Math.min(3, cleaned.length())).toLowerCase(Locale.ENGLISH)
                    + cleaned.substring(Math.min(3, cleaned.length()));
            YearMonth ym = YearMonth.parse(normalized, MONTH_YEAR_FORMAT);
            return ym.atDay(1);
        } catch (RuntimeException e) {
            // Catches DateTimeParseException (a RuntimeException subclass - Java disallows
            // listing both in a multi-catch) plus NumberFormatException/
            // StringIndexOutOfBoundsException from the parsing above. Any of these just
            // means "not a date we recognize" - return null and let the caller treat this
            // date as unknown rather than fail the whole resume parse over one bad field.
            return null;
        }
    }
}
