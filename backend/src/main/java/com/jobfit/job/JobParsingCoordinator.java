package com.jobfit.job;

import com.jobfit.jobparsing.DeterministicJobExtractor;
import com.jobfit.jobparsing.JobExtractionModels;
import com.jobfit.skill.Skill;
import com.jobfit.skill.SkillCategory;
import com.jobfit.skill.SkillNormalizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Async JD structuring pipeline - mirrors ResumeParsingCoordinator's shape
 * (separate bean from JobService for the same @Async self-invocation
 * reason). Runs DeterministicJobExtractor over the raw description, then
 * resolves inline skill mentions against the shared taxonomy and persists
 * JobRequirement rows.
 */
@Service
public class JobParsingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(JobParsingCoordinator.class);

    private final JobRepository jobRepository;
    private final JobRequirementRepository requirementRepository;
    private final DeterministicJobExtractor extractor;
    private final SkillNormalizationService skillNormalizationService;

    public JobParsingCoordinator(JobRepository jobRepository, JobRequirementRepository requirementRepository,
                                  DeterministicJobExtractor extractor,
                                  SkillNormalizationService skillNormalizationService) {
        this.jobRepository = jobRepository;
        this.requirementRepository = requirementRepository;
        this.extractor = extractor;
        this.skillNormalizationService = skillNormalizationService;
    }

    @Async
    @Transactional
    public void parseAsync(Long jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("parseAsync called for job {} which no longer exists", jobId);
            return;
        }
        job.markProcessing();
        jobRepository.save(job);

        try {
            JobExtractionModels.ExtractionResult extraction = extractor.extract(job.getRawDescription());
            int order = 0;
            Set<Long> domainSkillsAdded = new LinkedHashSet<>();

            for (JobExtractionModels.RequirementLine line : extraction.lines()) {
                RequirementType type = switch (line.bucket()) {
                    case REQUIRED -> RequirementType.REQUIRED_SKILL;
                    case PREFERRED -> RequirementType.PREFERRED_SKILL;
                    case RESPONSIBILITY -> RequirementType.RESPONSIBILITY;
                    case EDUCATION -> RequirementType.EDUCATION;
                };

                List<Skill> mentioned = skillNormalizationService.findMentionedSkills(line.text());
                Long primarySkillId = mentioned.isEmpty() ? null : mentioned.get(0).getId();

                requirementRepository.save(new JobRequirement(jobId, type, line.text(), primarySkillId, order++));

                if (line.bucket() == JobExtractionModels.Bucket.RESPONSIBILITY) {
                    for (Skill skill : mentioned) {
                        if (skill.getCategory() == SkillCategory.DOMAIN && domainSkillsAdded.add(skill.getId())) {
                            requirementRepository.save(new JobRequirement(
                                    jobId, RequirementType.DOMAIN, line.text(), skill.getId(), order++));
                        }
                    }
                }
            }

            if (extraction.experienceYearsSnippet() != null) {
                requirementRepository.save(new JobRequirement(
                        jobId, RequirementType.EXPERIENCE_YEARS, extraction.experienceYearsSnippet(), null, order++));
            }

            order = persistSoftSkillMentions(job, order);

            job.markReady();
            jobRepository.save(job);
        } catch (RuntimeException e) {
            log.error("Unexpected error parsing job {}", jobId, e);
            job.markFailed("An unexpected error occurred while parsing this job description.");
            jobRepository.save(job);
        }
    }

    private int persistSoftSkillMentions(Job job, int order) {
        Set<Long> softSkillsAdded = new LinkedHashSet<>();
        String[] lines = job.getRawDescription().replace("\r\n", "\n").split("\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            for (Skill skill : skillNormalizationService.findMentionedSkills(line)) {
                if (skill.getCategory() == SkillCategory.SOFT && softSkillsAdded.add(skill.getId())) {
                    requirementRepository.save(new JobRequirement(
                            job.getId(), RequirementType.SOFT_SKILL, line.strip(), skill.getId(), order++));
                }
            }
        }
        return order;
    }
}
