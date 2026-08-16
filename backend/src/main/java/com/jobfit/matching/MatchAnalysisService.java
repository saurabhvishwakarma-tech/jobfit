package com.jobfit.matching;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.job.Job;
import com.jobfit.job.JobRepository;
import com.jobfit.job.JobRequirement;
import com.jobfit.job.JobRequirementRepository;
import com.jobfit.job.RequirementType;
import com.jobfit.matching.dto.*;
import com.jobfit.resume.*;
import com.jobfit.scoring.ResumeSkillSource;
import com.jobfit.scoring.ScoringEngine;
import com.jobfit.skill.Skill;
import com.jobfit.skill.SkillCategory;
import com.jobfit.skill.SkillNormalizationService;
import com.jobfit.skill.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jobfit.scoring.ScoringModels.*;

@Service
public class MatchAnalysisService {

    private static final Pattern LEADING_NUMBER = Pattern.compile("(\\d{1,2})");

    private final ResumeRepository resumeRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceHighlightRepository highlightRepository;
    private final EducationRepository educationRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final JobRepository jobRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final SkillRepository skillRepository;
    private final SkillNormalizationService skillNormalizationService;
    private final MatchAnalysisRepository matchAnalysisRepository;
    private final ScoreComponentRepository scoreComponentRepository;
    private final EvidenceRepository evidenceRepository;
    private final ScoringEngine scoringEngine;

    public MatchAnalysisService(ResumeRepository resumeRepository, ExperienceRepository experienceRepository,
                                 ExperienceHighlightRepository highlightRepository,
                                 EducationRepository educationRepository, ResumeSkillRepository resumeSkillRepository,
                                 JobRepository jobRepository, JobRequirementRepository jobRequirementRepository,
                                 SkillRepository skillRepository, SkillNormalizationService skillNormalizationService,
                                 MatchAnalysisRepository matchAnalysisRepository,
                                 ScoreComponentRepository scoreComponentRepository,
                                 EvidenceRepository evidenceRepository, ScoringEngine scoringEngine) {
        this.resumeRepository = resumeRepository;
        this.experienceRepository = experienceRepository;
        this.highlightRepository = highlightRepository;
        this.educationRepository = educationRepository;
        this.resumeSkillRepository = resumeSkillRepository;
        this.jobRepository = jobRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.skillRepository = skillRepository;
        this.skillNormalizationService = skillNormalizationService;
        this.matchAnalysisRepository = matchAnalysisRepository;
        this.scoreComponentRepository = scoreComponentRepository;
        this.evidenceRepository = evidenceRepository;
        this.scoringEngine = scoringEngine;
    }

    @Transactional
    public MatchAnalysisDetailResponse analyse(Long userId, Long jobId, Long requestedResumeId) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", jobId));
        if (job.getParseStatus() != com.jobfit.job.ParseStatus.READY) {
            throw new IllegalStateException("This job hasn't finished parsing yet.");
        }

        Resume resume = requestedResumeId != null
                ? resumeRepository.findByIdAndUserId(requestedResumeId, userId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Resume", requestedResumeId))
                : resumeRepository.findByUserIdAndCurrentTrue(userId)
                        .orElseThrow(() -> new IllegalStateException("You don't have a resume uploaded yet."));
        if (resume.getParseStatus() != com.jobfit.resume.ParseStatus.READY) {
            throw new IllegalStateException("This resume hasn't finished parsing yet.");
        }

        ScoringInput scoringInput = buildScoringInput(job, resume);
        ScoringResult result = scoringEngine.score(scoringInput);

        MatchAnalysis analysis = new MatchAnalysis(resume.getId(), job.getId(), result.overallScore(),
                result.recommendation(), result.recommendationReason());
        analysis = matchAnalysisRepository.save(analysis);

        int order = 0;
        for (ScoreComponentResult c : result.components()) {
            scoreComponentRepository.save(new ScoreComponent(analysis.getId(), c.category(),
                    BigDecimal.valueOf(c.maxPoints()), BigDecimal.valueOf(c.earnedPoints()), c.explanation(), order++));
        }
        for (EvidenceResult e : result.evidence()) {
            evidenceRepository.save(new Evidence(analysis.getId(), e.requirementId(), e.matchType(), e.strength(),
                    e.resumeRefType(), e.resumeRefId(), e.explanationText(),
                    e.confidence() == null ? null : BigDecimal.valueOf(e.confidence())));
        }

        return getDetail(userId, analysis.getId());
    }

    @Transactional(readOnly = true)
    public MatchAnalysisDetailResponse getDetail(Long userId, Long matchAnalysisId) {
        MatchAnalysis analysis = matchAnalysisRepository.findById(matchAnalysisId)
                .orElseThrow(() -> ResourceNotFoundException.of("MatchAnalysis", matchAnalysisId));
        Resume resume = resumeRepository.findByIdAndUserId(analysis.getResumeId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("MatchAnalysis", matchAnalysisId));
        Job job = jobRepository.findById(analysis.getJobId())
                .orElseThrow(() -> ResourceNotFoundException.of("Job", analysis.getJobId()));

        List<ScoreComponentDto> components = scoreComponentRepository
                .findAllByMatchAnalysisIdOrderByDisplayOrder(analysis.getId()).stream()
                .map(c -> new ScoreComponentDto(c.getCategory(), c.getMaxPoints().doubleValue(),
                        c.getEarnedPoints().doubleValue(), c.getExplanation()))
                .toList();

        List<Evidence> evidenceRows = evidenceRepository.findAllByMatchAnalysisId(analysis.getId());
        List<EvidenceDto> evidenceDtos = buildEvidenceDtos(evidenceRows, resume.getId());

        return new MatchAnalysisDetailResponse(analysis.getId(), resume.getId(), job.getId(), job.getTitle(),
                job.getCompany(), analysis.getOverallScore(), analysis.getRecommendation(),
                analysis.getRecommendationReason(), analysis.getCreatedAt(), components, evidenceDtos);
    }

    // ---------- Building ScoringInput from persisted resume/job data ----------

    private ScoringInput buildScoringInput(Job job, Resume resume) {
        List<Experience> experiences = experienceRepository.findAllByResumeIdOrderByDisplayOrder(resume.getId());
        List<Long> experienceIds = experiences.stream().map(Experience::getId).toList();
        List<ExperienceHighlight> allHighlights = experienceIds.isEmpty()
                ? List.of() : highlightRepository.findAllByExperienceIdIn(experienceIds);

        double totalYears = computeTotalYearsExperience(experiences);
        List<String> jobTitles = experiences.stream().map(Experience::getJobTitle).toList();
        List<HighlightInput> highlightInputs = allHighlights.stream()
                .map(h -> new HighlightInput(h.getId(), h.getText())).toList();

        List<ResumeSkillInput> skillInputs = buildResumeSkillInputs(resume);

        List<EducationInput> educationInputs = educationRepository
                .findAllByResumeIdOrderByDisplayOrder(resume.getId()).stream()
                .map(e -> new EducationInput(e.getId(),
                        String.join(" ", nonNull(e.getDegree()), nonNull(e.getFieldOfStudy())).strip(),
                        DegreeLevelClassifier.ordinalFor(e.getDegree())))
                .toList();

        ResumeProfile resumeProfile = new ResumeProfile(totalYears, jobTitles, highlightInputs, skillInputs,
                educationInputs);

        List<JobRequirement> requirements = jobRequirementRepository.findAllByJobIdOrderByDisplayOrder(job.getId());
        Map<RequirementType, List<JobRequirement>> byType = requirements.stream()
                .collect(Collectors.groupingBy(JobRequirement::getType));

        Integer requiredYears = null;
        Long experienceYearsRequirementId = null;
        JobRequirement yearsReq = byType.getOrDefault(RequirementType.EXPERIENCE_YEARS, List.of())
                .stream().findFirst().orElse(null);
        if (yearsReq != null) {
            Matcher m = LEADING_NUMBER.matcher(yearsReq.getRawText());
            if (m.find()) {
                requiredYears = Integer.parseInt(m.group(1));
            }
            experienceYearsRequirementId = yearsReq.getId();
        }

        int requiredEducationLevel = byType.getOrDefault(RequirementType.EDUCATION, List.of()).stream()
                .mapToInt(r -> DegreeLevelClassifier.ordinalFor(r.getRawText())).max().orElse(0);

        return new ScoringInput(
                job.getTitle(),
                toRequirementInputs(byType.get(RequirementType.REQUIRED_SKILL)),
                toRequirementInputs(byType.get(RequirementType.PREFERRED_SKILL)),
                toRequirementInputs(byType.get(RequirementType.RESPONSIBILITY)),
                toRequirementInputs(byType.get(RequirementType.DOMAIN)),
                toRequirementInputs(byType.get(RequirementType.EDUCATION)),
                toRequirementInputs(byType.get(RequirementType.SOFT_SKILL)),
                requiredYears,
                experienceYearsRequirementId,
                requiredEducationLevel,
                resumeProfile);
    }

    private List<ResumeSkillInput> buildResumeSkillInputs(Resume resume) {
        List<ResumeSkill> resumeSkills = resumeSkillRepository.findAllByResumeId(resume.getId());
        Map<Long, Skill> skillsById = skillRepository.findAllById(
                        resumeSkills.stream().map(ResumeSkill::getSkillId).toList()).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<ResumeSkillInput> inputs = new ArrayList<>();
        Set<Long> seenSkillIds = new HashSet<>();
        for (ResumeSkill rs : resumeSkills) {
            Skill skill = skillsById.get(rs.getSkillId());
            if (skill == null) continue;
            inputs.add(new ResumeSkillInput(skill.getId(), skill.getCanonicalName(),
                    rs.getSource() == SkillSource.EXPLICIT ? ResumeSkillSource.EXPLICIT : ResumeSkillSource.INFERRED,
                    rs.getEvidenceHighlightId()));
            seenSkillIds.add(skill.getId());
        }

        // Soft skills rarely appear in a resume's dedicated Skills section, so they're not
        // captured by Phase 2's parsing - scan the raw resume text the same way job
        // descriptions are scanned for soft-skill mentions (see JobParsingCoordinator).
        if (resume.getRawText() != null) {
            for (Skill skill : skillNormalizationService.findMentionedSkills(resume.getRawText())) {
                if (skill.getCategory() == SkillCategory.SOFT && seenSkillIds.add(skill.getId())) {
                    inputs.add(new ResumeSkillInput(skill.getId(), skill.getCanonicalName(),
                            ResumeSkillSource.EXPLICIT, null));
                }
            }
        }
        return inputs;
    }

    private double computeTotalYearsExperience(List<Experience> experiences) {
        double totalDays = 0;
        for (Experience e : experiences) {
            if (e.getStartDate() == null) continue;
            LocalDate end = e.isCurrent() || e.getEndDate() == null ? LocalDate.now() : e.getEndDate();
            if (end.isBefore(e.getStartDate())) continue;
            totalDays += ChronoUnit.DAYS.between(e.getStartDate(), end);
        }
        return totalDays / 365.25;
    }

    private List<RequirementInput> toRequirementInputs(List<JobRequirement> requirements) {
        if (requirements == null) return List.of();
        return requirements.stream()
                .map(r -> new RequirementInput(r.getId(), r.getRawText(), r.getNormalizedSkillId()))
                .toList();
    }

    private String nonNull(String s) {
        return s == null ? "" : s;
    }

    // ---------- Evidence display resolution ----------

    private List<EvidenceDto> buildEvidenceDtos(List<Evidence> evidenceRows, Long resumeId) {
        Map<Long, JobRequirement> requirementsById = jobRequirementRepository
                .findAllById(evidenceRows.stream().map(Evidence::getJobRequirementId).toList()).stream()
                .collect(Collectors.toMap(JobRequirement::getId, Function.identity()));

        List<Long> highlightIds = evidenceRows.stream()
                .filter(e -> "EXPERIENCE_HIGHLIGHT".equals(e.getResumeRefType()) && e.getResumeRefId() != null)
                .map(Evidence::getResumeRefId).toList();
        Map<Long, String> highlightTextById = highlightIds.isEmpty() ? Map.of()
                : highlightRepository.findAllById(highlightIds).stream()
                        .collect(Collectors.toMap(ExperienceHighlight::getId, ExperienceHighlight::getText));

        List<Long> skillRefIds = evidenceRows.stream()
                .filter(e -> "SKILL".equals(e.getResumeRefType()) && e.getResumeRefId() != null)
                .map(Evidence::getResumeRefId).toList();
        Map<Long, String> skillNameById = skillRefIds.isEmpty() ? Map.of()
                : skillRepository.findAllById(skillRefIds).stream()
                        .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));

        List<Long> educationRefIds = evidenceRows.stream()
                .filter(e -> "EDUCATION".equals(e.getResumeRefType()) && e.getResumeRefId() != null)
                .map(Evidence::getResumeRefId).toList();
        Map<Long, String> educationDescById = educationRefIds.isEmpty() ? Map.of()
                : educationRepository.findAllById(educationRefIds).stream()
                        .collect(Collectors.toMap(Education::getId,
                                e -> String.join(" ", nonNull(e.getDegree()), nonNull(e.getInstitution())).strip()));

        List<EvidenceDto> dtos = new ArrayList<>();
        for (Evidence e : evidenceRows) {
            JobRequirement requirement = requirementsById.get(e.getJobRequirementId());
            String resumeRefText = switch (String.valueOf(e.getResumeRefType())) {
                case "EXPERIENCE_HIGHLIGHT" -> highlightTextById.get(e.getResumeRefId());
                case "SKILL" -> skillNameById.get(e.getResumeRefId());
                case "EDUCATION" -> educationDescById.get(e.getResumeRefId());
                default -> null;
            };
            dtos.add(new EvidenceDto(
                    e.getJobRequirementId(),
                    requirement == null ? null : requirement.getType().name(),
                    requirement == null ? null : requirement.getRawText(),
                    e.getMatchType().name(), e.getStrength().name(),
                    e.getResumeRefType(), e.getResumeRefId(), resumeRefText,
                    e.getExplanationText(), e.getConfidence() == null ? null : e.getConfidence().doubleValue()));
        }
        return dtos;
    }
}
