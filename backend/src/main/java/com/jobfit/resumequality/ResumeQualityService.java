package com.jobfit.resumequality;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.resume.*;
import com.jobfit.resumequality.dto.QualityIssueDto;
import com.jobfit.resumequality.dto.ResumeQualityResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.jobfit.resumequality.QualityModels.*;

/**
 * Orchestration for Resume Quality Analysis: pulls the current resume's
 * already-parsed data (contact info, experiences, bullets, skill count)
 * and hands it to the pure ResumeQualityAnalyzer. Deliberately low
 * dependency - everything it needs lives in the `resume` module already
 * (see docs/JobFit_Design_v1.md, "standalone, low-dependency"). Results
 * are computed fresh on every request rather than persisted - they're
 * cheap to recompute and always reflect the current parsed data.
 */
@Service
public class ResumeQualityService {

    private final ResumeRepository resumeRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceHighlightRepository highlightRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final ResumeQualityAnalyzer analyzer;

    public ResumeQualityService(ResumeRepository resumeRepository, ContactInfoRepository contactInfoRepository,
                                 ExperienceRepository experienceRepository,
                                 ExperienceHighlightRepository highlightRepository,
                                 ResumeSkillRepository resumeSkillRepository, ResumeQualityAnalyzer analyzer) {
        this.resumeRepository = resumeRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.experienceRepository = experienceRepository;
        this.highlightRepository = highlightRepository;
        this.resumeSkillRepository = resumeSkillRepository;
        this.analyzer = analyzer;
    }

    @Transactional(readOnly = true)
    public ResumeQualityResponse analyze(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Resume", resumeId));
        if (resume.getParseStatus() != ParseStatus.READY) {
            throw new IllegalStateException("This resume hasn't finished parsing yet.");
        }

        Optional<ContactInfo> contactInfo = contactInfoRepository.findByResumeId(resumeId);
        boolean hasEmail = contactInfo.map(c -> notBlank(c.getEmail())).orElse(false);
        boolean hasPhone = contactInfo.map(c -> notBlank(c.getPhone())).orElse(false);

        List<Experience> experiences = experienceRepository.findAllByResumeIdOrderByDisplayOrder(resumeId);
        List<Long> experienceIds = experiences.stream().map(Experience::getId).toList();
        List<ExperienceHighlight> allHighlights = experienceIds.isEmpty() ? List.of()
                : highlightRepository.findAllByExperienceIdIn(experienceIds);

        Map<Long, List<ExperienceHighlight>> highlightsByExperience = allHighlights.stream()
                .collect(Collectors.groupingBy(ExperienceHighlight::getExperienceId));

        List<ExperienceInput> experienceInputs = experiences.stream()
                .map(e -> new ExperienceInput(e.getId(), e.getJobTitle(),
                        highlightsByExperience.getOrDefault(e.getId(), List.of()).size()))
                .toList();

        List<BulletInput> bulletInputs = allHighlights.stream()
                .map(h -> new BulletInput(h.getId(), h.getText()))
                .toList();

        int skillCount = resumeSkillRepository.findAllByResumeId(resumeId).size();

        QualityInput input = new QualityInput(hasEmail, hasPhone, experienceInputs, skillCount, bulletInputs);
        QualityResult result = analyzer.analyze(input);

        List<QualityIssueDto> issueDtos = result.issues().stream()
                .map(i -> new QualityIssueDto(i.category(), i.severity().name(), i.message(),
                        i.resumeRefType(), i.resumeRefId()))
                .toList();

        int high = countBySeverity(result, IssueSeverity.HIGH);
        int medium = countBySeverity(result, IssueSeverity.MEDIUM);
        int low = countBySeverity(result, IssueSeverity.LOW);

        return new ResumeQualityResponse(resumeId, result.score(), high, medium, low, issueDtos);
    }

    private int countBySeverity(QualityResult result, IssueSeverity severity) {
        return (int) result.issues().stream().filter(i -> i.severity() == severity).count();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
