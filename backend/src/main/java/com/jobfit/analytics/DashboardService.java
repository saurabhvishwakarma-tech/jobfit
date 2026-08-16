package com.jobfit.analytics;

import com.jobfit.analytics.dto.BestFitRoleDto;
import com.jobfit.analytics.dto.DashboardResponse;
import com.jobfit.analytics.dto.SkillFrequencyDto;
import com.jobfit.application.Application;
import com.jobfit.application.ApplicationRepository;
import com.jobfit.application.ApplicationStatus;
import com.jobfit.application.ApplicationStatusHistory;
import com.jobfit.application.ApplicationStatusHistoryRepository;
import com.jobfit.job.Job;
import com.jobfit.job.JobRepository;
import com.jobfit.job.JobRequirement;
import com.jobfit.job.JobRequirementRepository;
import com.jobfit.job.RequirementType;
import com.jobfit.matching.Evidence;
import com.jobfit.matching.EvidenceRepository;
import com.jobfit.matching.MatchAnalysis;
import com.jobfit.matching.MatchAnalysisRepository;
import com.jobfit.resume.ResumeRepository;
import com.jobfit.resume.ResumeSkill;
import com.jobfit.resume.ResumeSkillRepository;
import com.jobfit.resume.SkillSource;
import com.jobfit.scoring.EvidenceStrength;
import com.jobfit.skill.Skill;
import com.jobfit.skill.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates data that already exists in the job/application/matching/resume
 * modules into a single dashboard view. Deliberately does no scoring or AI
 * work of its own - it reads what those modules already computed and
 * persisted (see docs/JobFit_Design_v1.md, Dashboard requirements).
 */
@Service
public class DashboardService {

    private static final int TOP_N = 5;

    private final JobRepository jobRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final MatchAnalysisRepository matchAnalysisRepository;
    private final EvidenceRepository evidenceRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final SkillRepository skillRepository;

    public DashboardService(JobRepository jobRepository, JobRequirementRepository jobRequirementRepository,
                             ApplicationRepository applicationRepository,
                             ApplicationStatusHistoryRepository historyRepository,
                             MatchAnalysisRepository matchAnalysisRepository, EvidenceRepository evidenceRepository,
                             ResumeRepository resumeRepository, ResumeSkillRepository resumeSkillRepository,
                             SkillRepository skillRepository) {
        this.jobRepository = jobRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.matchAnalysisRepository = matchAnalysisRepository;
        this.evidenceRepository = evidenceRepository;
        this.resumeRepository = resumeRepository;
        this.resumeSkillRepository = resumeSkillRepository;
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId) {
        List<Job> jobs = jobRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        Map<Long, Job> jobsById = jobs.stream().collect(Collectors.toMap(Job::getId, Function.identity()));

        List<Application> applications = applicationRepository.findAllByUserId(userId);

        List<MatchAnalysis> analyses = jobIds.isEmpty() ? List.of()
                : matchAnalysisRepository.findAllByJobIdInOrderByCreatedAtDesc(jobIds);
        Map<Long, MatchAnalysis> latestAnalysisByJob = latestPerJob(analyses);

        Double averageFitScore = averageScore(latestAnalysisByJob.values());

        long interviews = countApplicationsThatReached(applications,
                ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
        long offers = countApplicationsThatReached(applications, ApplicationStatus.OFFER);

        List<JobRequirement> requirements = jobIds.isEmpty() ? List.of()
                : jobRequirementRepository.findAllByJobIdInOrderByDisplayOrder(jobIds);
        Map<Long, JobRequirement> requirementsById = requirements.stream()
                .collect(Collectors.toMap(JobRequirement::getId, Function.identity()));

        List<SkillFrequencyDto> mostRequestedSkills = topSkillsByCount(
                countBySkill(requirements.stream().filter(this::isSkillRequirement)));

        List<Long> analysisIds = analyses.stream().map(MatchAnalysis::getId).toList();
        List<Evidence> evidence = analysisIds.isEmpty() ? List.of()
                : evidenceRepository.findAllByMatchAnalysisIdIn(analysisIds);
        List<SkillFrequencyDto> commonSkillGaps = topSkillsByCount(
                countBySkill(evidence.stream()
                        .filter(e -> e.getStrength() == EvidenceStrength.MISSING)
                        .map(e -> requirementsById.get(e.getJobRequirementId()))
                        .filter(r -> r != null && isSkillRequirement(r))));

        List<String> strongestSkills = strongestResumeSkills(userId);

        List<BestFitRoleDto> bestFitRoles = latestAnalysisByJob.values().stream()
                .sorted(Comparator.comparingInt(MatchAnalysis::getOverallScore).reversed())
                .limit(TOP_N)
                .map(a -> {
                    Job job = jobsById.get(a.getJobId());
                    return new BestFitRoleDto(a.getJobId(), job == null ? null : job.getTitle(),
                            job == null ? null : job.getCompany(), a.getOverallScore());
                })
                .toList();

        return new DashboardResponse(jobs.size(), latestAnalysisByJob.size(), applications.size(),
                (int) interviews, (int) offers, averageFitScore, mostRequestedSkills, strongestSkills,
                commonSkillGaps, bestFitRoles);
    }

    private boolean isSkillRequirement(JobRequirement requirement) {
        return requirement.getNormalizedSkillId() != null
                && (requirement.getType() == RequirementType.REQUIRED_SKILL
                        || requirement.getType() == RequirementType.PREFERRED_SKILL);
    }

    /**
     * `analyses` is already sorted by createdAt descending (across all jobs).
     * The first time a jobId is seen while walking that order is necessarily
     * its most recent analysis, regardless of how other jobs interleave.
     */
    private Map<Long, MatchAnalysis> latestPerJob(List<MatchAnalysis> analyses) {
        Map<Long, MatchAnalysis> latest = new LinkedHashMap<>();
        for (MatchAnalysis analysis : analyses) {
            latest.putIfAbsent(analysis.getJobId(), analysis);
        }
        return latest;
    }

    private Double averageScore(java.util.Collection<MatchAnalysis> analyses) {
        if (analyses.isEmpty()) return null;
        double avg = analyses.stream().mapToInt(MatchAnalysis::getOverallScore).average().orElse(0);
        return Math.round(avg * 10) / 10.0;
    }

    private long countApplicationsThatReached(List<Application> applications, ApplicationStatus... statuses) {
        if (applications.isEmpty()) return 0;
        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        List<ApplicationStatusHistory> history = historyRepository.findAllByApplicationIdIn(applicationIds);

        Map<Long, Set<ApplicationStatus>> reachedByApplication = new HashMap<>();
        for (ApplicationStatusHistory h : history) {
            reachedByApplication.computeIfAbsent(h.getApplicationId(), k -> EnumSet.noneOf(ApplicationStatus.class))
                    .add(h.getStatus());
        }
        Set<ApplicationStatus> target = EnumSet.copyOf(java.util.Arrays.asList(statuses));

        return applicationIds.stream()
                .filter(id -> {
                    Set<ApplicationStatus> reached = reachedByApplication.get(id);
                    return reached != null && !java.util.Collections.disjoint(reached, target);
                })
                .count();
    }

    private Map<Long, Long> countBySkill(java.util.stream.Stream<JobRequirement> requirements) {
        return requirements.collect(Collectors.groupingBy(JobRequirement::getNormalizedSkillId, Collectors.counting()));
    }

    private List<SkillFrequencyDto> topSkillsByCount(Map<Long, Long> countsBySkillId) {
        if (countsBySkillId.isEmpty()) return List.of();
        List<Map.Entry<Long, Long>> sorted = countsBySkillId.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .toList();
        Map<Long, String> names = skillRepository.findAllById(sorted.stream().map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));
        return sorted.stream()
                .map(e -> new SkillFrequencyDto(names.getOrDefault(e.getKey(), "Unknown skill"), e.getValue()))
                .toList();
    }

    private List<String> strongestResumeSkills(Long userId) {
        return resumeRepository.findByUserIdAndCurrentTrue(userId)
                .map(resume -> {
                    List<Long> explicitSkillIds = resumeSkillRepository.findAllByResumeId(resume.getId()).stream()
                            .filter(s -> s.getSource() == SkillSource.EXPLICIT)
                            .map(ResumeSkill::getSkillId)
                            .distinct()
                            .toList();
                    if (explicitSkillIds.isEmpty()) return List.<String>of();
                    return skillRepository.findAllById(explicitSkillIds).stream()
                            .map(Skill::getCanonicalName)
                            .sorted()
                            .toList();
                })
                .orElse(List.of());
    }
}
