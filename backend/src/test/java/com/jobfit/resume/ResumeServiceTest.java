package com.jobfit.resume;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.resume.dto.ResumeUpdateRequest;
import com.jobfit.resume.storage.ResumeStorageService;
import com.jobfit.resumeparsing.UnparsableFileException;
import com.jobfit.skill.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private ContactInfoRepository contactInfoRepository;
    @Mock private ExperienceRepository experienceRepository;
    @Mock private ExperienceHighlightRepository highlightRepository;
    @Mock private EducationRepository educationRepository;
    @Mock private CertificationRepository certificationRepository;
    @Mock private ProjectEntryRepository projectRepository;
    @Mock private ResumeSkillRepository resumeSkillRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private ResumeStorageService storageService;
    @Mock private ResumeParsingCoordinator parsingCoordinator;

    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(resumeRepository, contactInfoRepository, experienceRepository,
                highlightRepository, educationRepository, certificationRepository, projectRepository,
                resumeSkillRepository, skillRepository, storageService, parsingCoordinator);
    }

    @Test
    void upload_rejectsNonPdfContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "not a pdf".getBytes());

        assertThatThrownBy(() -> resumeService.upload(1L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");

        verifyNoInteractions(storageService, parsingCoordinator);
    }

    @Test
    void upload_rejectsFileWithPdfContentTypeButWrongMagicBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
                "application/pdf", "definitely not a real pdf".getBytes());

        assertThatThrownBy(() -> resumeService.upload(1L, file))
                .isInstanceOf(UnparsableFileException.class)
                .hasMessageContaining("not a valid PDF");
    }

    @Test
    void upload_storesFileAndTriggersAsyncParse_onValidPdf() {
        byte[] pdfBytes = "%PDF-1.4 minimal valid-looking header".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);

        when(resumeRepository.findByUserIdAndCurrentTrue(1L)).thenReturn(Optional.empty());
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> {
            Resume r = invocation.getArgument(0);
            if (r.getId() == null) setId(r, 100L);
            return r;
        });
        when(storageService.store(eq(1L), eq(100L), eq("resume.pdf"), any())).thenReturn("user-1/resume-100.pdf");

        resumeService.upload(1L, file);

        verify(storageService).store(eq(1L), eq(100L), eq("resume.pdf"), any());
        verify(parsingCoordinator).parseAsync(100L);
    }

    // ---------- IDOR prevention: every lookup is scoped by (id, userId) and returns
    // 404 (ResourceNotFoundException), never a 403, so a caller can't tell the
    // difference between "not yours" and "doesn't exist" (see SecurityUtils / the
    // README security notes). These mirror the same pattern verified in the job,
    // application, matching, and resumequality service tests. ----------

    @Test
    void getDetail_throwsNotFound_whenResumeNotOwnedByUser() {
        when(resumeRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.getDetail(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_throwsNotFound_whenResumeNotOwnedByUser() {
        when(resumeRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        ResumeUpdateRequest request = new ResumeUpdateRequest(
                new com.jobfit.resume.dto.ContactInfoDto(null, null, null, null, null, null, null),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());

        assertThatThrownBy(() -> resumeService.update(1L, 10L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(contactInfoRepository, experienceRepository, educationRepository,
                certificationRepository, projectRepository);
    }

    @Test
    void delete_throwsNotFound_whenResumeNotOwnedByUser() {
        when(resumeRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.delete(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(storageService);
        verify(resumeRepository, never()).delete(any());
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
