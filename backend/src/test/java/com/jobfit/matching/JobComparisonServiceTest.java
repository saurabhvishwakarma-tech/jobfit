package com.jobfit.matching;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.job.Job;
import com.jobfit.job.JobRepository;
import com.jobfit.job.JobRequirement;
import com.jobfit.job.JobRequirementRepository;
import com.jobfit.job.RequirementType;
import com.jobfit.matching.dto.ComparedJobDto;
import com.jobfit.matching.dto.JobComparisonResponse;
import com.jobfit.matching.dto.SkillComparisonRow;
import com.jobfit.resume.Resume;
import com.jobfit.resume.ResumeRepository;
import com.jobfit.resume.ResumeSkill;
import com.jobfit.resume.ResumeSkillRepository;
import com.jobfit.resume.SkillSource;
import com.jobfit.skill.Skill;
import com.jobfit.skill.SkillCategory;
import com.jobfit.skill.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * JobComparisonService is pure aggregation/reshaping over other modules'
 * repositories, so - same discipline as DashboardServiceTest - every test
 * hand-builds a small fixture and hand-traces the expected output rather
 * than asserting against whatever the implementation happens to produce.
 */
@ExtendWith(MockitoExtension.class)
class JobComparisonServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobRequirementRepository jobRequirementRepository;
    @Mock private MatchAnalysisRepository matchAnalysisRepository;
    @Mock private ScoreComponentRepository scoreComponentRepository;
    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeSkillRepository resumeSkillRepository;
    @Mock private SkillRepository skillRepository;

    private JobComparisonService service;

    private static final Long USER_ID = 1L;

    private Skill java, python, sql, git;

    @BeforeEach
    void setUp() throws Exception {
        service = new JobComparisonService(jobRepository, jobRequirementRepository, matchAnalysisRepository,
                scoreComponentRepository, resumeRepository, resumeSkillRepository, skillRepository);

        java = newSkill(100L, "Java");
        python = newSkill(101L, "Python");
        sql = newSkill(102L, "SQL");
        git = newSkill(103L, "Git");

        lenient().when(skillRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any()))
                .thenAnswer(inv -> {
                    Iterable<Long> idsIterable = inv.getArgument(0);
                    List<Long> ids = new java.util.ArrayList<>();
                    idsIterable.forEach(ids::add);
                    return List.of(java, python, sql, git).stream().filter(s -> ids.contains(s.getId())).toList();
                });
    }

    @Test
    void rejectsFewerThanTwoJobs() {
        assertThatThrownBy(() -> service.compare(USER_ID, List.of(10L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    void rejectsMoreThanFiveJobs() {
        assertThatThrownBy(() -> service.compare(USER_ID, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 5");
    }

    @Test
    void throwsNotFound_whenAJobIsNotOwnedByTheUser() throws Exception {
        Job jobA = newJob(10L, "Backend Engineer", "Acme");
        when(jobRepository.findAllByIdInAndUserId(List.of(10L, 99L), USER_ID)).thenReturn(List.of(jobA));

        assertThatThrownBy(() -> service.compare(USER_ID, List.of(10L, 99L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void comparesAnalysedAndUnanalysedJobsAndBuildsSkillOverlap() throws Exception {
        Job jobA = newJob(10L, "Backend Engineer", "Acme");
        Job jobB = newJob(11L, "Platform Engineer", "Beta");
        Job jobC = newJob(12L, "Data Engineer", "Gamma");
        // DB returns them in a different order than requested - service must restore request order.
        when(jobRepository.findAllByIdInAndUserId(List.of(10L, 11L, 12L), USER_ID))
                .thenReturn(List.of(jobC, jobB, jobA));

        MatchAnalysis analysisA = newMatchAnalysis(500L, 10L, 85, "STRONG_MATCH");
        MatchAnalysis analysisB = newMatchAnalysis(501L, 11L, 60, "REASONABLE_MATCH");
        when(matchAnalysisRepository.findAllByJobIdInOrderByCreatedAtDesc(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(analysisA, analysisB)); // job C never analysed

        when(scoreComponentRepository.findAllByMatchAnalysisIdInOrderByDisplayOrder(List.of(500L, 501L)))
                .thenReturn(List.of(
                        newComponent(500L, "Required skills", 35, 30, 0),
                        newComponent(500L, "Experience", 20, 15, 1),
                        newComponent(501L, "Required skills", 35, 20, 0)
                ));

        JobRequirement reqAJava = newRequirement(200L, 10L, RequirementType.REQUIRED_SKILL, java.getId());
        JobRequirement reqASql = newRequirement(201L, 10L, RequirementType.REQUIRED_SKILL, sql.getId());
        JobRequirement reqBJava = newRequirement(202L, 11L, RequirementType.REQUIRED_SKILL, java.getId());
        JobRequirement reqBPython = newRequirement(203L, 11L, RequirementType.PREFERRED_SKILL, python.getId());
        JobRequirement reqCJava = newRequirement(204L, 12L, RequirementType.REQUIRED_SKILL, java.getId());
        JobRequirement reqCGit = newRequirement(205L, 12L, RequirementType.PREFERRED_SKILL, git.getId());
        when(jobRequirementRepository.findAllByJobIdInOrderByDisplayOrder(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(reqAJava, reqASql, reqBJava, reqBPython, reqCJava, reqCGit));

        Resume resume = newResume(400L);
        when(resumeRepository.findByUserIdAndCurrentTrue(USER_ID)).thenReturn(Optional.of(resume));
        when(resumeSkillRepository.findAllByResumeId(400L)).thenReturn(List.of(
                new ResumeSkill(400L, java.getId(), SkillSource.EXPLICIT, null),
                new ResumeSkill(400L, python.getId(), SkillSource.INFERRED, null)
        ));
        // SQL and Git are absent from the resume entirely.

        JobComparisonResponse response = service.compare(USER_ID, List.of(10L, 11L, 12L));

        assertThat(response.jobs()).hasSize(3);
        assertThat(response.jobs()).extracting(ComparedJobDto::jobId).containsExactly(10L, 11L, 12L); // request order restored

        ComparedJobDto compA = response.jobs().get(0);
        assertThat(compA.analysed()).isTrue();
        assertThat(compA.matchAnalysisId()).isEqualTo(500L);
        assertThat(compA.overallScore()).isEqualTo(85);
        assertThat(compA.categoryScores()).hasSize(2);

        ComparedJobDto compB = response.jobs().get(1);
        assertThat(compB.overallScore()).isEqualTo(60);
        assertThat(compB.categoryScores()).hasSize(1);

        ComparedJobDto compC = response.jobs().get(2);
        assertThat(compC.analysed()).isFalse();
        assertThat(compC.matchAnalysisId()).isNull();
        assertThat(compC.overallScore()).isNull();
        assertThat(compC.categoryScores()).isEmpty();

        // Java is required by all 3 jobs -> sorts first. Remaining ties (SQL, Python, Git,
        // each required/preferred by exactly one job) break alphabetically: Git, Python, SQL.
        assertThat(response.skillComparison()).extracting(SkillComparisonRow::skillName)
                .containsExactly("Java", "Git", "Python", "SQL");

        SkillComparisonRow javaRow = response.skillComparison().get(0);
        assertThat(javaRow.requirementPerJob()).containsExactly("REQUIRED", "REQUIRED", "REQUIRED");
        assertThat(javaRow.resumeStatus()).isEqualTo("EXPLICIT");

        SkillComparisonRow sqlRow = response.skillComparison().stream()
                .filter(r -> r.skillName().equals("SQL")).findFirst().orElseThrow();
        assertThat(sqlRow.requirementPerJob()).containsExactly("REQUIRED", null, null);
        assertThat(sqlRow.resumeStatus()).isEqualTo("ABSENT");

        SkillComparisonRow pythonRow = response.skillComparison().stream()
                .filter(r -> r.skillName().equals("Python")).findFirst().orElseThrow();
        assertThat(pythonRow.requirementPerJob()).containsExactly(null, "PREFERRED", null);
        assertThat(pythonRow.resumeStatus()).isEqualTo("INFERRED");

        SkillComparisonRow gitRow = response.skillComparison().stream()
                .filter(r -> r.skillName().equals("Git")).findFirst().orElseThrow();
        assertThat(gitRow.requirementPerJob()).containsExactly(null, null, "PREFERRED");
        assertThat(gitRow.resumeStatus()).isEqualTo("ABSENT");
    }

    private static Skill newSkill(Long id, String name) throws Exception {
        Skill skill = new Skill(name, SkillCategory.LANGUAGE);
        setId(skill, id);
        return skill;
    }

    private static Job newJob(Long id, String title, String company) throws Exception {
        Job job = new Job(USER_ID, title, company, "raw description", null);
        setId(job, id);
        return job;
    }

    private static MatchAnalysis newMatchAnalysis(Long id, Long jobId, int score, String recommendation) throws Exception {
        MatchAnalysis analysis = new MatchAnalysis(400L, jobId, score, recommendation, "reason");
        setId(analysis, id);
        return analysis;
    }

    private static ScoreComponent newComponent(Long matchAnalysisId, String category, double max, double earned,
                                                int order) throws Exception {
        ScoreComponent component = new ScoreComponent(matchAnalysisId, category, BigDecimal.valueOf(max),
                BigDecimal.valueOf(earned), "explanation", order);
        setId(component, matchAnalysisId * 100 + order);
        return component;
    }

    private static JobRequirement newRequirement(Long id, Long jobId, RequirementType type, Long skillId) throws Exception {
        JobRequirement requirement = new JobRequirement(jobId, type, "raw text", skillId, 0);
        setId(requirement, id);
        return requirement;
    }

    private static Resume newResume(Long id) throws Exception {
        Resume resume = new Resume(USER_ID, 1, "resume.pdf", "storage-key");
        setId(resume, id);
        return resume;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
