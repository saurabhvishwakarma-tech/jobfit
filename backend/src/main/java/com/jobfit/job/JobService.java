package com.jobfit.job;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.job.dto.*;
import com.jobfit.skill.Skill;
import com.jobfit.skill.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobRequirementRepository requirementRepository;
    private final SkillRepository skillRepository;
    private final JobParsingCoordinator parsingCoordinator;

    public JobService(JobRepository jobRepository, JobRequirementRepository requirementRepository,
                       SkillRepository skillRepository, JobParsingCoordinator parsingCoordinator) {
        this.jobRepository = jobRepository;
        this.requirementRepository = requirementRepository;
        this.skillRepository = skillRepository;
        this.parsingCoordinator = parsingCoordinator;
    }

    @Transactional
    public JobSummaryResponse create(Long userId, JobCreateRequest request) {
        Job job = new Job(userId, request.title().strip(),
                request.company() == null ? null : request.company().strip(),
                request.rawDescription(), request.sourceUrl());
        job = jobRepository.save(job);

        // Deferred to afterCommit - see ResumeService.upload() for why:
        // firing the async parse job while this transaction is still open
        // can race the new thread's own DB connection against our
        // not-yet-committed insert, leaving the job stuck "pending" forever.
        Long jobId = job.getId();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            parsingCoordinator.parseAsync(jobId);
                        }
                    });
        } else {
            parsingCoordinator.parseAsync(jobId);
        }

        return JobSummaryResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<JobSummaryResponse> list(Long userId) {
        return jobRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(JobSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getDetail(Long userId, Long jobId) {
        Job job = ownedJobOrThrow(userId, jobId);
        List<JobRequirement> requirements = requirementRepository.findAllByJobIdOrderByDisplayOrder(jobId);

        Map<Long, Skill> skillsById = skillRepository.findAllById(
                        requirements.stream().map(JobRequirement::getNormalizedSkillId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Skill::getId, Function.identity()));

        Map<RequirementType, List<JobRequirementDto>> grouped = requirements.stream()
                .collect(Collectors.groupingBy(JobRequirement::getType,
                        Collectors.mapping(r -> toDto(r, skillsById), Collectors.toList())));

        JobRequirementDto experienceYears = grouped.getOrDefault(RequirementType.EXPERIENCE_YEARS, List.of())
                .stream().findFirst().orElse(null);

        return JobDetailResponse.of(job,
                grouped.getOrDefault(RequirementType.REQUIRED_SKILL, List.of()),
                grouped.getOrDefault(RequirementType.PREFERRED_SKILL, List.of()),
                grouped.getOrDefault(RequirementType.RESPONSIBILITY, List.of()),
                grouped.getOrDefault(RequirementType.EDUCATION, List.of()),
                grouped.getOrDefault(RequirementType.DOMAIN, List.of()),
                grouped.getOrDefault(RequirementType.SOFT_SKILL, List.of()),
                experienceYears);
    }

    @Transactional
    public void delete(Long userId, Long jobId) {
        Job job = ownedJobOrThrow(userId, jobId);
        jobRepository.delete(job);
    }

    private Job ownedJobOrThrow(Long userId, Long jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", jobId));
    }

    private JobRequirementDto toDto(JobRequirement r, Map<Long, Skill> skillsById) {
        Skill skill = r.getNormalizedSkillId() == null ? null : skillsById.get(r.getNormalizedSkillId());
        return new JobRequirementDto(r.getId(), r.getType().name(), r.getRawText(),
                skill == null ? null : skill.getCanonicalName());
    }
}
