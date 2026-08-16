package com.jobfit.resumequality;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.resume.*;
import com.jobfit.resumequality.dto.ResumeQualityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Uses a real ResumeQualityAnalyzer (like MatchAnalysisServiceTest uses a
 * real ScoringEngine) - it's a pure function, so wiring it in for real here
 * actually verifies the entity -> QualityInput translation is correct, not
 * just that some mock was called.
 */
@ExtendWith(MockitoExtension.class)
class ResumeQualityServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private ContactInfoRepository contactInfoRepository;
    @Mock private ExperienceRepository experienceRepository;
    @Mock private ExperienceHighlightRepository highlightRepository;
    @Mock private ResumeSkillRepository resumeSkillRepository;

    private ResumeQualityService service;

    @BeforeEach
    void setUp() {
        service = new ResumeQualityService(resumeRepository, contactInfoRepository, experienceRepository,
                highlightRepository, resumeSkillRepository, new ResumeQualityAnalyzer());
    }

    @Test
    void analyze_throwsNotFound_whenResumeNotOwnedByUser() {
        when(resumeRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void analyze_throwsIllegalState_whenResumeStillParsing() throws Exception {
        Resume resume = newResume(10L); // markReady() never called - stays PENDING
        when(resumeRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(resume));

        assertThatThrownBy(() -> service.analyze(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hasn't finished parsing");
    }

    @Test
    void analyze_buildsQualityReportFromPersistedResumeData() throws Exception {
        Resume resume = newResume(10L);
        resume.markReady("raw text");
        when(resumeRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(resume));

        ContactInfo contactInfo = new ContactInfo(10L);
        contactInfo.setEmail("me@example.com");
        // phone left blank on purpose - the report should flag it as missing.
        when(contactInfoRepository.findByResumeId(10L)).thenReturn(Optional.of(contactInfo));

        Experience experience = newExperience(100L, "Software Engineer");
        when(experienceRepository.findAllByResumeIdOrderByDisplayOrder(10L)).thenReturn(List.of(experience));

        ExperienceHighlight highlight = newHighlight(200L, 100L,
                "Reduced infrastructure costs by 15% through right-sizing.");
        when(highlightRepository.findAllByExperienceIdIn(List.of(100L))).thenReturn(List.of(highlight));

        when(resumeSkillRepository.findAllByResumeId(10L)).thenReturn(List.of(
                new ResumeSkill(10L, 1L, SkillSource.EXPLICIT, null)));

        ResumeQualityResponse response = service.analyze(1L, 10L);

        assertThat(response.resumeId()).isEqualTo(10L);
        // Only issues here: missing phone (MEDIUM) and fewer than 3 total bullets (MEDIUM) -
        // the single bullet itself is clean (quantified, reasonable length, no weak phrasing).
        assertThat(response.mediumCount()).isEqualTo(2);
        assertThat(response.highCount()).isZero();
        assertThat(response.issues()).extracting("category")
                .containsExactlyInAnyOrder("Contact Info", "Structure");
        // 100 - (2 * 5) = 90
        assertThat(response.score()).isEqualTo(90);
    }

    private static Resume newResume(Long id) throws Exception {
        Resume resume = new Resume(1L, 1, "resume.pdf", "storage-key");
        setId(resume, id);
        return resume;
    }

    private static Experience newExperience(Long id, String jobTitle) throws Exception {
        Experience experience = new Experience(10L, jobTitle, "Acme");
        setId(experience, id);
        return experience;
    }

    private static ExperienceHighlight newHighlight(Long id, Long experienceId, String text) throws Exception {
        ExperienceHighlight highlight = new ExperienceHighlight(experienceId, text, 0);
        setId(highlight, id);
        return highlight;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
