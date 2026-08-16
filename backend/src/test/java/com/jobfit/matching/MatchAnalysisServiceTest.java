package com.jobfit.matching;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.job.Job;
import com.jobfit.job.JobRepository;
import com.jobfit.job.JobRequirementRepository;
import com.jobfit.resume.*;
import com.jobfit.scoring.ScoringEngine;
import com.jobfit.skill.SkillNormalizationService;
import com.jobfit.skill.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchAnalysisServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private ExperienceRepository experienceRepository;
    @Mock private ExperienceHighlightRepository highlightRepository;
    @Mock private EducationRepository educationRepository;
    @Mock private ResumeSkillRepository resumeSkillRepository;
    @Mock private JobRepository jobRepository;
    @Mock private JobRequirementRepository jobRequirementRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillNormalizationService skillNormalizationService;
    @Mock private MatchAnalysisRepository matchAnalysisRepository;
    @Mock private ScoreComponentRepository scoreComponentRepository;
    @Mock private EvidenceRepository evidenceRepository;

    private MatchAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new MatchAnalysisService(resumeRepository, experienceRepository, highlightRepository,
                educationRepository, resumeSkillRepository, jobRepository, jobRequirementRepository,
                skillRepository, skillNormalizationService, matchAnalysisRepository, scoreComponentRepository,
                evidenceRepository, new ScoringEngine());
    }

    @Test
    void analyse_throwsNotFound_whenJobDoesNotBelongToUser() {
        when(jobRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyse(1L, 99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void analyse_throwsIllegalState_whenJobStillParsing() throws Exception {
        Job pendingJob = newJob(5L, com.jobfit.job.ParseStatus.PROCESSING);
        when(jobRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(pendingJob));

        assertThatThrownBy(() -> service.analyse(1L, 5L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hasn't finished parsing");
    }

    @Test
    void analyse_throwsIllegalState_whenUserHasNoResume() throws Exception {
        Job readyJob = newJob(5L, com.jobfit.job.ParseStatus.READY);
        when(jobRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(readyJob));
        when(resumeRepository.findByUserIdAndCurrentTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyse(1L, 5L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resume");
    }

    @Test
    void getDetail_throwsNotFound_whenAnalysisResumeBelongsToAnotherUser() {
        MatchAnalysis analysis = new MatchAnalysis(7L, 8L, 80, "STRONG_MATCH", "reason");
        when(matchAnalysisRepository.findById(42L)).thenReturn(Optional.of(analysis));
        when(resumeRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(1L, 42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Job's constructor is package-private; tests build instances via reflection like the entity itself intends. */
    private static Job newJob(Long id, com.jobfit.job.ParseStatus status) throws Exception {
        Constructor<Job> ctor = Job.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Job job = ctor.newInstance();
        setField(job, "id", id);
        setField(job, "parseStatus", status);
        return job;
    }

    private static void setField(Object entity, String fieldName, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, value);
    }
}
