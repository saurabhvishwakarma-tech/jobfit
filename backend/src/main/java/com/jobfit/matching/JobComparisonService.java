package com.jobfit.matching;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.job.Job;
import com.jobfit.job.JobRepository;
import com.jobfit.job.JobRequirement;
import com.jobfit.job.JobRequirementRepository;
import com.jobfit.job.RequirementType;
import com.jobfit.matching.dto.ComparedJobDto;
import com.jobfit.matching.dto.JobComparisonResponse;
import com.jobfit.matching.dto.ScoreComponentDto;
import com.jobfit.matching.dto.SkillComparisonRow;
import com.jobfit.resume.ResumeRepository;
import com.jobfit.resume.ResumeSkill;
import com.jobfit.resume.ResumeSkillRepository;
import com.jobfit.resume.SkillSource;
import com.jobfit.skill.Skill;
import com.jobfit.skill.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Puts 2-5 of the user's own jobs side by side: their latest fit score and
 * category breakdown (if analysed), and which required/preferred skills
 * each one asks for versus what the user's current resume actually shows.
 * Read-only - reuses whatever MatchAnalysisService already scored and
 * persisted; never re-scores or calls the AI layer itself.
 */
@Service
public class JobComparisonService {

    private static final int MIN_JOBS = 2;
    private static final int MAX_JOBS = 5;

    private final JobRepository jobRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final MatchAnalysisRepository matchAnalysisRepository;
    private final ScoreComponentRepository scoreComponentRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final SkillRepository skillRepository;

    public JobComparisonService(JobRepository jobRepository, JobRequirementRepository jobRequirementRepository,
                                 MatchAnalysisRepository matchAnalysisRepository,
                                 ScoreComponentRepository scoreComponentRepository, ResumeRepository resumeRepository,
                                 ResumeSkillRepository resumeSkillRepository, SkillRepository skillRepository) {
        this.jobRepository = jobRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.matchAnalysisRepository = matchAnalysisRepository;
        this.scoreComponentRepository = scoreComponentRepository;
        this.resumeRepository = resumeRepository;
        this.resumeSkillRepository = resumeSkillRepository;
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public JobComparisonResponse compare(Long userId, List<Long> requestedJobIds) {
        List<Long> jobIds = requestedJobIds.stream().distinct().toList();
        if (jobIds.size() < MIN_JOBS) {
            throw new IllegalArgumentException("Select at least " + MIN_JOBS + " jobs to compare.");
        }
        if (jobIds.size() > MAX_JOBS) {
            throw new IllegalArgumentException("You can compare at most " + MAX_JOBS + " jobs at once.");
        }

        List<Job> ownedJobs = jobRepository.findAllByIdInAndUserId(jobIds, userId);
        Map<Long, Job> jobsById = ownedJobs.stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        for (Long id : jobIds) {
            if (!jobsById.containsKey(id)) {
                throw ResourceNotFoundException.of("Job", id);
            }
        }
        // preserve the order the caller asked for, not whatever order the DB returned
        List<Job> jobs = jobIds.stream().map(jobsById::get).toList();

        Map<Long, MatchAnalysis> latestAnalysisByJob = latestAnalysisPerJob(jobIds);

        List<Long> analysisIds = latestAnalysisByJob.values().stream().map(MatchAnalysis::getId).toList();
        Map<Long, List<ScoreComponentDto>> componentsByAnalysisId = analysisIds.isEmpty() ? Map.of()
                : scoreComponentRepository.findAllByMatchAnalysisIdInOrderByDisplayOrder(analysisIds).stream()
                        .collect(Collectors.groupingBy(ScoreComponent::getMatchAnalysisId, LinkedHashMap::new,
                                Collectors.mapping(c -> new ScoreComponentDto(c.getCategory(),
                                        c.getMaxPoints().doubleValue(), c.getEarnedPoints().doubleValue(),
                                        c.getExplanation()), Collectors.toList())));

        List<ComparedJobDto> comparedJobs = jobs.stream().map(job -> {
            MatchAnalysis analysis = latestAnalysisByJob.get(job.getId());
            if (analysis == null) {
                return new ComparedJobDto(job.getId(), job.getTitle(), job.getCompany(), false,
                        null, null, null, List.of());
            }
            return new ComparedJobDto(job.getId(), job.getTitle(), job.getCompany(), true,
                    analysis.getId(), analysis.getOverallScore(), analysis.getRecommendation(),
                    componentsByAnalysisId.getOrDefault(analysis.getId(), List.of()));
        }).toList();

        List<SkillComparisonRow> skillComparison = buildSkillComparison(userId, jobIds, jobs);

        return new JobComparisonResponse(comparedJobs, skillComparison);
    }

    /**
     * Rows already come back ordered by createdAt descending, so the first
     * time a jobId is seen while walking that order is its latest analysis.
     */
    private Map<Long, MatchAnalysis> latestAnalysisPerJob(List<Long> jobIds) {
        Map<Long, MatchAnalysis> latest = new LinkedHashMap<>();
        for (MatchAnalysis a : matchAnalysisRepository.findAllByJobIdInOrderByCreatedAtDesc(jobIds)) {
            latest.putIfAbsent(a.getJobId(), a);
        }
        return latest;
    }

    private List<SkillComparisonRow> buildSkillComparison(Long userId, List<Long> jobIds, List<Job> jobs) {
        List<JobRequirement> requirements = jobRequirementRepository.findAllByJobIdInOrderByDisplayOrder(jobIds)
                .stream()
                .filter(r -> r.getNormalizedSkillId() != null
                        && (r.getType() == RequirementType.REQUIRED_SKILL
                                || r.getType() == RequirementType.PREFERRED_SKILL))
                .toList();
        if (requirements.isEmpty()) {
            return List.of();
        }

        // skillId -> jobId -> "REQUIRED"/"PREFERRED" (REQUIRED wins if a job somehow lists both)
        Map<Long, Map<Long, String>> bySkillThenJob = new LinkedHashMap<>();
        for (JobRequirement r : requirements) {
            Map<Long, String> perJob = bySkillThenJob.computeIfAbsent(r.getNormalizedSkillId(), k -> new LinkedHashMap<>());
            String type = r.getType() == RequirementType.REQUIRED_SKILL ? "REQUIRED" : "PREFERRED";
            perJob.merge(r.getJobId(), type, (existing, incoming) -> "REQUIRED".equals(existing) ? existing : incoming);
        }

        Map<Long, String> skillNames = skillRepository.findAllById(bySkillThenJob.keySet()).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));

        Map<Long, SkillSource> resumeSkillSources = currentResumeSkillSources(userId);

        List<Long> jobIdOrder = jobs.stream().map(Job::getId).toList();

        List<SkillComparisonRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, String>> entry : bySkillThenJob.entrySet()) {
            Long skillId = entry.getKey();
            Map<Long, String> perJob = entry.getValue();
            List<String> requirementPerJob = jobIdOrder.stream().map(perJob::get).toList();
            SkillSource source = resumeSkillSources.get(skillId);
            String resumeStatus = source == null ? "ABSENT" : source.name();
            rows.add(new SkillComparisonRow(skillNames.getOrDefault(skillId, "Unknown skill"),
                    requirementPerJob, resumeStatus));
        }

        // most-requested (across the compared jobs) first, then alphabetical for stable ties
        rows.sort(Comparator
                .comparingLong((SkillComparisonRow r) -> r.requirementPerJob().stream().filter(Objects::nonNull).count())
                .reversed()
                .thenComparing(SkillComparisonRow::skillName));
        return rows;
    }

    private Map<Long, SkillSource> currentResumeSkillSources(Long userId) {
        return resumeRepository.findByUserIdAndCurrentTrue(userId)
                .map(resume -> resumeSkillRepository.findAllByResumeId(resume.getId()).stream()
                        .collect(Collectors.toMap(ResumeSkill::getSkillId, ResumeSkill::getSource,
                                (a, b) -> a == SkillSource.EXPLICIT ? a : b)))
                .orElse(Map.of());
    }
}
