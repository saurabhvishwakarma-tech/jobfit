package com.jobfit.skill;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic, dictionary-based skill lookup - step 1 of the matching
 * algorithm's normalization pipeline (see docs/JobFit_Design_v1.md). Tries
 * an exact canonical-name match first, then a known alias. Anything that
 * doesn't resolve here is a candidate for AI-assisted equivalence
 * suggestion (handled separately, always tagged INFERRED, never silently
 * promoted to an explicit match).
 */
@Service
public class SkillNormalizationService {

    private final SkillRepository skillRepository;
    private final SkillAliasRepository skillAliasRepository;

    public SkillNormalizationService(SkillRepository skillRepository, SkillAliasRepository skillAliasRepository) {
        this.skillRepository = skillRepository;
        this.skillAliasRepository = skillAliasRepository;
    }

    /** Resolves free-text (e.g. "JS", "postgres") to a canonical Skill, if known. */
    public Optional<Skill> resolve(String rawTerm) {
        String trimmed = rawTerm.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        Optional<Skill> direct = skillRepository.findByCanonicalNameIgnoreCase(trimmed);
        if (direct.isPresent()) {
            return direct;
        }
        return skillAliasRepository.findByAliasIgnoreCase(trimmed)
                .flatMap(alias -> skillRepository.findById(alias.getSkillId()));
    }

    /**
     * Scans free text (a job requirement bullet, a full JD, etc.) for any
     * known skill or alias appearing as a whole word/phrase, case-insensitive.
     * Used where skills are mentioned inline in a sentence rather than listed
     * as standalone terms (e.g. job requirement bullets) - step 1 of the
     * matching algorithm's normalization pipeline applied to prose instead
     * of a comma-separated list.
     */
    public List<Skill> findMentionedSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Skill> allSkills = skillRepository.findAll();
        List<SkillAlias> allAliases = skillAliasRepository.findAll();
        Map<Long, Skill> skillsById = allSkills.stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));

        // Longest terms first so "Spring Boot" is preferred over a bare "Spring" alias match.
        List<Term> terms = new ArrayList<>();
        for (Skill s : allSkills) {
            terms.add(new Term(s.getCanonicalName(), s));
        }
        for (SkillAlias a : allAliases) {
            Skill owner = skillsById.get(a.getSkillId());
            if (owner != null) {
                terms.add(new Term(a.getAlias(), owner));
            }
        }
        terms.sort((a, b) -> Integer.compare(b.text.length(), a.text.length()));

        LinkedHashSet<Skill> matches = new LinkedHashSet<>();
        for (Term term : terms) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(term.text) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) {
                matches.add(term.skill);
            }
        }
        return new ArrayList<>(matches);
    }

    private record Term(String text, Skill skill) {
    }
}
