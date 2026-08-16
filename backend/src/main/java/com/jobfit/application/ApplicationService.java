package com.jobfit.application;

import com.jobfit.application.dto.*;
import com.jobfit.common.exception.DuplicateResourceException;
import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.job.Job;
import com.jobfit.job.JobRepository;
import com.jobfit.matching.MatchAnalysis;
import com.jobfit.matching.MatchAnalysisRepository;
import com.jobfit.resume.Resume;
import com.jobfit.resume.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final MatchAnalysisRepository matchAnalysisRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
                               ApplicationStatusHistoryRepository historyRepository, JobRepository jobRepository,
                               ResumeRepository resumeRepository, MatchAnalysisRepository matchAnalysisRepository) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.matchAnalysisRepository = matchAnalysisRepository;
    }

    @Transactional
    public ApplicationDetailResponse create(Long userId, ApplicationCreateRequest request) {
        Job job = jobRepository.findByIdAndUserId(request.jobId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", request.jobId()));

        if (applicationRepository.findByUserIdAndJobId(userId, job.getId()).isPresent()) {
            throw new DuplicateResourceException("You already have an application tracked for this job.");
        }

        Long resumeId = null;
        if (request.resumeId() != null) {
            Resume resume = resumeRepository.findByIdAndUserId(request.resumeId(), userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Resume", request.resumeId()));
            resumeId = resume.getId();
        }

        Long matchAnalysisId = null;
        if (request.matchAnalysisId() != null) {
            MatchAnalysis analysis = matchAnalysisRepository.findById(request.matchAnalysisId())
                    .filter(a -> a.getJobId().equals(job.getId()))
                    .orElseThrow(() -> ResourceNotFoundException.of("MatchAnalysis", request.matchAnalysisId()));
            matchAnalysisId = analysis.getId();
        }

        Application application = new Application(userId, job.getId(), resumeId, matchAnalysisId);
        application.setNotes(request.notes());
        application = applicationRepository.save(application);
        historyRepository.save(new ApplicationStatusHistory(application.getId(), ApplicationStatus.SAVED, null));

        return getDetail(userId, application.getId());
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummaryResponse> list(Long userId) {
        List<Application> applications = applicationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId);
        if (applications.isEmpty()) {
            return List.of();
        }

        Map<Long, Job> jobsById = jobRepository.findAllById(
                        applications.stream().map(Application::getJobId).toList()).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));

        List<Long> analysisIds = applications.stream().map(Application::getMatchAnalysisId)
                .filter(java.util.Objects::nonNull).toList();
        Map<Long, Integer> scoreByAnalysisId = analysisIds.isEmpty() ? Map.of()
                : matchAnalysisRepository.findAllById(analysisIds).stream()
                        .collect(Collectors.toMap(MatchAnalysis::getId, MatchAnalysis::getOverallScore));

        return applications.stream().map(a -> {
            Job job = jobsById.get(a.getJobId());
            Integer score = a.getMatchAnalysisId() == null ? null : scoreByAnalysisId.get(a.getMatchAnalysisId());
            return new ApplicationSummaryResponse(a.getId(), a.getJobId(),
                    job == null ? null : job.getTitle(), job == null ? null : job.getCompany(),
                    a.getResumeId(), score, a.getStatus().name(), a.getAppliedAt(), a.getUpdatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDetailResponse getDetail(Long userId, Long applicationId) {
        Application application = ownedApplicationOrThrow(userId, applicationId);
        Job job = jobRepository.findById(application.getJobId())
                .orElseThrow(() -> ResourceNotFoundException.of("Job", application.getJobId()));

        Integer matchScore = null;
        String matchRecommendation = null;
        if (application.getMatchAnalysisId() != null) {
            Optional<MatchAnalysis> analysis = matchAnalysisRepository.findById(application.getMatchAnalysisId());
            matchScore = analysis.map(MatchAnalysis::getOverallScore).orElse(null);
            matchRecommendation = analysis.map(MatchAnalysis::getRecommendation).orElse(null);
        }

        List<StatusHistoryDto> history = historyRepository
                .findAllByApplicationIdOrderByChangedAtAsc(application.getId()).stream()
                .map(h -> new StatusHistoryDto(h.getStatus().name(), h.getNotes(), h.getChangedAt()))
                .toList();

        return new ApplicationDetailResponse(application.getId(), job.getId(), job.getTitle(), job.getCompany(),
                application.getResumeId(), application.getMatchAnalysisId(), matchScore, matchRecommendation,
                application.getStatus().name(), application.getNotes(), application.getAppliedAt(),
                application.getCreatedAt(), application.getUpdatedAt(), history);
    }

    @Transactional
    public ApplicationDetailResponse updateStatus(Long userId, Long applicationId, ApplicationStatusUpdateRequest request) {
        Application application = ownedApplicationOrThrow(userId, applicationId);
        ApplicationStatus newStatus = parseStatus(request.status());

        application.changeStatus(newStatus);
        applicationRepository.save(application);
        historyRepository.save(new ApplicationStatusHistory(application.getId(), newStatus, request.notes()));

        return getDetail(userId, applicationId);
    }

    @Transactional
    public ApplicationDetailResponse updateNotes(Long userId, Long applicationId, ApplicationNotesUpdateRequest request) {
        Application application = ownedApplicationOrThrow(userId, applicationId);
        application.setNotes(request.notes());
        applicationRepository.save(application);
        return getDetail(userId, applicationId);
    }

    @Transactional
    public void delete(Long userId, Long applicationId) {
        Application application = ownedApplicationOrThrow(userId, applicationId);
        applicationRepository.delete(application);
    }

    private Application ownedApplicationOrThrow(Long userId, Long applicationId) {
        return applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Application", applicationId));
    }

    private ApplicationStatus parseStatus(String raw) {
        try {
            return ApplicationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown application status: " + raw);
        }
    }
}
