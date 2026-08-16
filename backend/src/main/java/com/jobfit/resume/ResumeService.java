package com.jobfit.resume;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.resume.dto.*;
import com.jobfit.resume.storage.ResumeStorageService;
import com.jobfit.resumeparsing.UnparsableFileException;
import com.jobfit.skill.Skill;
import com.jobfit.skill.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf");

    private final ResumeRepository resumeRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceHighlightRepository highlightRepository;
    private final EducationRepository educationRepository;
    private final CertificationRepository certificationRepository;
    private final ProjectEntryRepository projectRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final SkillRepository skillRepository;
    private final ResumeStorageService storageService;
    private final ResumeParsingCoordinator parsingCoordinator;

    public ResumeService(ResumeRepository resumeRepository, ContactInfoRepository contactInfoRepository,
                          ExperienceRepository experienceRepository, ExperienceHighlightRepository highlightRepository,
                          EducationRepository educationRepository, CertificationRepository certificationRepository,
                          ProjectEntryRepository projectRepository, ResumeSkillRepository resumeSkillRepository,
                          SkillRepository skillRepository, ResumeStorageService storageService,
                          ResumeParsingCoordinator parsingCoordinator) {
        this.resumeRepository = resumeRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.experienceRepository = experienceRepository;
        this.highlightRepository = highlightRepository;
        this.educationRepository = educationRepository;
        this.certificationRepository = certificationRepository;
        this.projectRepository = projectRepository;
        this.resumeSkillRepository = resumeSkillRepository;
        this.skillRepository = skillRepository;
        this.storageService = storageService;
        this.parsingCoordinator = parsingCoordinator;
    }

    @Transactional
    public ResumeSummaryResponse upload(Long userId, MultipartFile file) {
        validateFile(file);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UnparsableFileException("Could not read the uploaded file.", e);
        }
        if (content.length < 4 || content[0] != '%' || content[1] != 'P' || content[2] != 'D' || content[3] != 'F') {
            throw new UnparsableFileException("The uploaded file is not a valid PDF.");
        }

        int nextVersion = resumeRepository.findByUserIdAndCurrentTrue(userId)
                .map(r -> r.getVersionNo() + 1)
                .orElse(1);
        resumeRepository.clearCurrentFlagForUser(userId);

        Resume resume = new Resume(userId, nextVersion, file.getOriginalFilename(), "pending");
        resume = resumeRepository.save(resume);

        String storageKey = storageService.store(userId, resume.getId(), file.getOriginalFilename(), content);
        resume.setStorageKey(storageKey);
        resume = resumeRepository.save(resume);

        // Don't fire the async parse job until this transaction has actually
        // committed. parseAsync() runs on a separate thread with its own DB
        // connection - if it starts while we're still inside this
        // @Transactional method, it can query for a row that isn't visible
        // yet, find nothing, log a warning, and bail out silently, leaving
        // the resume stuck on "pending" forever. Deferring to afterCommit
        // guarantees the row exists before the parser ever looks for it.
        Long resumeId = resume.getId();
        registerAfterCommitParse(resumeId);

        return ResumeSummaryResponse.from(resume);
    }

    private void registerAfterCommitParse(Long resumeId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            parsingCoordinator.parseAsync(resumeId);
                        }
                    });
        } else {
            parsingCoordinator.parseAsync(resumeId);
        }
    }

    @Transactional(readOnly = true)
    public List<ResumeSummaryResponse> list(Long userId) {
        return resumeRepository.findAllByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(ResumeSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeDetailResponse getDetail(Long userId, Long resumeId) {
        Resume resume = ownedResumeOrThrow(userId, resumeId);
        return assembleDetail(resume);
    }

    @Transactional
    public ResumeDetailResponse update(Long userId, Long resumeId, ResumeUpdateRequest request) {
        Resume resume = ownedResumeOrThrow(userId, resumeId);

        ContactInfo contactInfo = contactInfoRepository.findByResumeId(resumeId).orElseGet(() -> new ContactInfo(resumeId));
        ContactInfoDto c = request.contactInfo();
        contactInfo.setFullName(c.fullName());
        contactInfo.setEmail(c.email());
        contactInfo.setPhone(c.phone());
        contactInfo.setLocation(c.location());
        contactInfo.setLinkedinUrl(c.linkedinUrl());
        contactInfo.setGithubUrl(c.githubUrl());
        contactInfo.setPortfolioUrl(c.portfolioUrl());
        contactInfoRepository.save(contactInfo);

        replaceExperiences(resumeId, request.experiences());
        replaceEducation(resumeId, request.education());
        replaceCertifications(resumeId, request.certifications());
        replaceProjects(resumeId, request.projects());

        return assembleDetail(resume);
    }

    @Transactional
    public void delete(Long userId, Long resumeId) {
        Resume resume = ownedResumeOrThrow(userId, resumeId);
        storageService.delete(resume.getStorageKey());
        resumeRepository.delete(resume); // FK cascades remove all child rows
    }

    // ---------- helpers ----------

    private Resume ownedResumeOrThrow(Long userId, Long resumeId) {
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Resume", resumeId));
    }

    private void replaceExperiences(Long resumeId, List<ExperienceDto> dtos) {
        List<Experience> existing = experienceRepository.findAllByResumeIdOrderByDisplayOrder(resumeId);
        List<Long> existingIds = existing.stream().map(Experience::getId).toList();
        if (!existingIds.isEmpty()) {
            highlightRepository.findAllByExperienceIdIn(existingIds).forEach(h -> highlightRepository.delete(h));
        }
        experienceRepository.deleteAll(existing);

        int order = 0;
        for (ExperienceDto dto : dtos) {
            Experience entity = new Experience(resumeId, dto.jobTitle(), dto.company());
            entity.setLocation(dto.location());
            entity.setStartDate(dto.startDate());
            entity.setEndDate(dto.endDate());
            entity.setCurrent(dto.current());
            entity.setDisplayOrder(order++);
            entity = experienceRepository.save(entity);

            int hOrder = 0;
            for (HighlightDto h : dto.highlights()) {
                highlightRepository.save(new ExperienceHighlight(entity.getId(), h.text(), hOrder++));
            }
        }
    }

    private void replaceEducation(Long resumeId, List<EducationDto> dtos) {
        educationRepository.deleteAll(educationRepository.findAllByResumeIdOrderByDisplayOrder(resumeId));
        int order = 0;
        for (EducationDto dto : dtos) {
            Education entity = new Education(resumeId, dto.institution());
            entity.setDegree(dto.degree());
            entity.setFieldOfStudy(dto.fieldOfStudy());
            entity.setStartDate(dto.startDate());
            entity.setEndDate(dto.endDate());
            entity.setDisplayOrder(order++);
            educationRepository.save(entity);
        }
    }

    private void replaceCertifications(Long resumeId, List<CertificationDto> dtos) {
        certificationRepository.deleteAll(certificationRepository.findAllByResumeIdOrderByDisplayOrder(resumeId));
        int order = 0;
        for (CertificationDto dto : dtos) {
            Certification entity = new Certification(resumeId, dto.name());
            entity.setIssuer(dto.issuer());
            entity.setIssuedDate(dto.issuedDate());
            entity.setDisplayOrder(order++);
            certificationRepository.save(entity);
        }
    }

    private void replaceProjects(Long resumeId, List<ProjectDto> dtos) {
        projectRepository.deleteAll(projectRepository.findAllByResumeIdOrderByDisplayOrder(resumeId));
        int order = 0;
        for (ProjectDto dto : dtos) {
            ProjectEntry entity = new ProjectEntry(resumeId, dto.name());
            entity.setDescription(dto.description());
            entity.setTechnologies(dto.technologies());
            entity.setDisplayOrder(order++);
            projectRepository.save(entity);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }
    }

    private ResumeDetailResponse assembleDetail(Resume resume) {
        Long resumeId = resume.getId();

        ContactInfoDto contactInfoDto = contactInfoRepository.findByResumeId(resumeId)
                .map(c -> new ContactInfoDto(c.getFullName(), c.getEmail(), c.getPhone(), c.getLocation(),
                        c.getLinkedinUrl(), c.getGithubUrl(), c.getPortfolioUrl()))
                .orElse(new ContactInfoDto(null, null, null, null, null, null, null));

        List<Experience> experiences = experienceRepository.findAllByResumeIdOrderByDisplayOrder(resumeId);
        List<Long> experienceIds = experiences.stream().map(Experience::getId).toList();
        Map<Long, List<ExperienceHighlight>> highlightsByExperience = experienceIds.isEmpty()
                ? Map.of()
                : highlightRepository.findAllByExperienceIdIn(experienceIds).stream()
                        .collect(Collectors.groupingBy(ExperienceHighlight::getExperienceId));

        List<ExperienceDto> experienceDtos = experiences.stream().map(e -> new ExperienceDto(
                e.getId(), e.getJobTitle(), e.getCompany(), e.getLocation(), e.getStartDate(), e.getEndDate(),
                e.isCurrent(),
                highlightsByExperience.getOrDefault(e.getId(), List.of()).stream()
                        .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                        .map(h -> new HighlightDto(h.getText()))
                        .toList()
        )).toList();

        List<EducationDto> educationDtos = educationRepository.findAllByResumeIdOrderByDisplayOrder(resumeId).stream()
                .map(e -> new EducationDto(e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(),
                        e.getStartDate(), e.getEndDate()))
                .toList();

        List<CertificationDto> certificationDtos =
                certificationRepository.findAllByResumeIdOrderByDisplayOrder(resumeId).stream()
                        .map(c -> new CertificationDto(c.getId(), c.getName(), c.getIssuer(), c.getIssuedDate()))
                        .toList();

        List<ProjectDto> projectDtos = projectRepository.findAllByResumeIdOrderByDisplayOrder(resumeId).stream()
                .map(p -> new ProjectDto(p.getId(), p.getName(), p.getDescription(), p.getTechnologies()))
                .toList();

        List<ResumeSkill> resumeSkills = resumeSkillRepository.findAllByResumeId(resumeId);
        Map<Long, Skill> skillsById = skillRepository.findAllById(
                        resumeSkills.stream().map(ResumeSkill::getSkillId).toList()).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        List<SkillTagDto> skillDtos = resumeSkills.stream()
                .map(rs -> {
                    Skill skill = skillsById.get(rs.getSkillId());
                    return skill == null ? null : new SkillTagDto(
                            skill.getId(), skill.getCanonicalName(), skill.getCategory().name(), rs.getSource().name());
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return ResumeDetailResponse.of(resume, contactInfoDto, experienceDtos, educationDtos,
                certificationDtos, projectDtos, skillDtos);
    }
}
