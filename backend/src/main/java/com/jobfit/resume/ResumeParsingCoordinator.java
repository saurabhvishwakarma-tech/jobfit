package com.jobfit.resume;

import com.jobfit.ai.AiClient;
import com.jobfit.resumeparsing.DeterministicResumeExtractor;
import com.jobfit.resumeparsing.ExtractionModels;
import com.jobfit.resumeparsing.PdfTextExtractor;
import com.jobfit.resumeparsing.UnparsableFileException;
import com.jobfit.resume.storage.ResumeStorageService;
import com.jobfit.skill.SkillNormalizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Runs the resume parsing pipeline off the request thread. This is a
 * separate bean from ResumeService on purpose: Spring's @Async proxying
 * only kicks in on calls that go through the bean proxy, and a method
 * calling another @Async method on `this` (self-invocation) would silently
 * run synchronously instead. Keeping it in its own bean and calling it from
 * ResumeService as `coordinator.parseAsync(id)` avoids that trap.
 */
@Service
public class ResumeParsingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingCoordinator.class);

    private final ResumeRepository resumeRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceHighlightRepository highlightRepository;
    private final EducationRepository educationRepository;
    private final CertificationRepository certificationRepository;
    private final ProjectEntryRepository projectRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final ResumeStorageService storageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final DeterministicResumeExtractor deterministicExtractor;
    private final SkillNormalizationService skillNormalizationService;
    private final AiClient aiClient;

    public ResumeParsingCoordinator(ResumeRepository resumeRepository, ContactInfoRepository contactInfoRepository,
                                     ExperienceRepository experienceRepository,
                                     ExperienceHighlightRepository highlightRepository,
                                     EducationRepository educationRepository,
                                     CertificationRepository certificationRepository,
                                     ProjectEntryRepository projectRepository,
                                     ResumeSkillRepository resumeSkillRepository,
                                     ResumeStorageService storageService, PdfTextExtractor pdfTextExtractor,
                                     DeterministicResumeExtractor deterministicExtractor,
                                     SkillNormalizationService skillNormalizationService, AiClient aiClient) {
        this.resumeRepository = resumeRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.experienceRepository = experienceRepository;
        this.highlightRepository = highlightRepository;
        this.educationRepository = educationRepository;
        this.certificationRepository = certificationRepository;
        this.projectRepository = projectRepository;
        this.resumeSkillRepository = resumeSkillRepository;
        this.storageService = storageService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.deterministicExtractor = deterministicExtractor;
        this.skillNormalizationService = skillNormalizationService;
        this.aiClient = aiClient;
    }

    /**
     * Fire-and-forget entry point called by ResumeService right after a
     * successful upload. The frontend polls GET /api/resumes/{id} and
     * watches parseStatus flip from PROCESSING to READY/FAILED.
     */
    @Async
    @Transactional
    public void parseAsync(Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            log.warn("parseAsync called for resume {} which no longer exists", resumeId);
            return;
        }
        resume.markProcessing();
        resumeRepository.save(resume);

        try {
            byte[] fileBytes = storageService.retrieve(resume.getStorageKey());
            String rawText = pdfTextExtractor.extractText(fileBytes);
            ExtractionModels.ExtractionResult extraction = deterministicExtractor.extract(rawText);

            persistContactInfo(resume.getId(), extraction.contactInfo());
            persistExperiences(resume.getId(), extraction.experiences());
            persistEducation(resume.getId(), extraction.education());
            persistCertifications(resume.getId(), extraction.certifications());
            persistProjects(resume.getId(), extraction.projects());
            persistSkills(resume.getId(), extraction.skillTerms(), rawText);

            resume.markReady(rawText);
            resumeRepository.save(resume);
        } catch (UnparsableFileException e) {
            log.info("Resume {} could not be parsed: {}", resumeId, e.getMessage());
            resume.markFailed(e.getMessage());
            resumeRepository.save(resume);
        } catch (RuntimeException e) {
            log.error("Unexpected error parsing resume {}", resumeId, e);
            resume.markFailed("An unexpected error occurred while parsing this resume.");
            resumeRepository.save(resume);
        }
    }

    private void persistContactInfo(Long resumeId, ExtractionModels.ContactInfo extracted) {
        ContactInfo info = new ContactInfo(resumeId);
        info.setFullName(extracted.fullName());
        info.setEmail(extracted.email());
        info.setPhone(extracted.phone());
        info.setLocation(extracted.location());
        info.setLinkedinUrl(extracted.linkedinUrl());
        info.setGithubUrl(extracted.githubUrl());
        info.setPortfolioUrl(extracted.portfolioUrl());
        contactInfoRepository.save(info);
    }

    private void persistExperiences(Long resumeId, List<ExtractionModels.Experience> extracted) {
        int order = 0;
        for (ExtractionModels.Experience e : extracted) {
            Experience entity = new Experience(resumeId,
                    e.jobTitle() == null || e.jobTitle().isBlank() ? "Untitled role" : e.jobTitle(),
                    e.company() == null ? "" : e.company());
            entity.setLocation(e.location());
            entity.setStartDate(e.startDate());
            entity.setEndDate(e.endDate());
            entity.setCurrent(e.current());
            entity.setDisplayOrder(order++);
            entity = experienceRepository.save(entity);

            int hOrder = 0;
            for (String highlightText : e.highlights()) {
                highlightRepository.save(new ExperienceHighlight(entity.getId(), highlightText, hOrder++));
            }
        }
    }

    private void persistEducation(Long resumeId, List<ExtractionModels.Education> extracted) {
        int order = 0;
        for (ExtractionModels.Education e : extracted) {
            Education entity = new Education(resumeId,
                    e.institution() == null || e.institution().isBlank() ? "Unknown institution" : e.institution());
            entity.setDegree(e.degree());
            entity.setFieldOfStudy(e.fieldOfStudy());
            entity.setStartDate(e.startDate());
            entity.setEndDate(e.endDate());
            entity.setDisplayOrder(order++);
            educationRepository.save(entity);
        }
    }

    private void persistCertifications(Long resumeId, List<ExtractionModels.Certification> extracted) {
        int order = 0;
        for (ExtractionModels.Certification c : extracted) {
            Certification entity = new Certification(resumeId, c.name());
            entity.setIssuer(c.issuer());
            entity.setIssuedDate(c.issuedDate());
            entity.setDisplayOrder(order++);
            certificationRepository.save(entity);
        }
    }

    private void persistProjects(Long resumeId, List<ExtractionModels.Project> extracted) {
        int order = 0;
        for (ExtractionModels.Project p : extracted) {
            ProjectEntry entity = new ProjectEntry(resumeId, p.name());
            entity.setDescription(p.description());
            entity.setTechnologies(p.technologies());
            entity.setDisplayOrder(order++);
            projectRepository.save(entity);
        }
    }

    private void persistSkills(Long resumeId, List<String> rawTerms, String rawText) {
        LinkedHashSet<String> explicitNames = new LinkedHashSet<>();
        for (String term : rawTerms) {
            skillNormalizationService.resolve(term).ifPresent(skill -> {
                if (!resumeSkillRepository.existsByResumeIdAndSkillId(resumeId, skill.getId())) {
                    resumeSkillRepository.save(new ResumeSkill(resumeId, skill.getId(), SkillSource.EXPLICIT, null));
                }
                explicitNames.add(skill.getCanonicalName());
            });
        }

        if (aiClient.isAvailable()) {
            List<String> suggestions = aiClient.suggestAdditionalResumeSkills(rawText, new ArrayList<>(explicitNames));
            for (String suggestion : suggestions) {
                skillNormalizationService.resolve(suggestion).ifPresent(skill -> {
                    if (!resumeSkillRepository.existsByResumeIdAndSkillId(resumeId, skill.getId())) {
                        resumeSkillRepository.save(
                                new ResumeSkill(resumeId, skill.getId(), SkillSource.INFERRED, null));
                    }
                });
            }
        }
    }
}
