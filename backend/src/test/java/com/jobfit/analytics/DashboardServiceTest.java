package com.jobfit.analytics;

import com.jobfit.analytics.dto.DashboardResponse;
import com.jobfit.application.Application;
import com.jobfit.application.ApplicationRepository;
import com.jobfit.application.ApplicationStatus;
import com.jobfit.application.ApplicationStatusHistory;
import com.jobfit.application.ApplicationStatusHistoryRepository;
import com.jobfit.job.Job;
import com.jobfit.job.JobRepository;
import com.jobfit.job.JobRequirement;
import com.jobfit.job.JobRequirementRepository;
import com.jobfit.job.RequirementType;
import com.jobfit.matching.Evidence;
import com.jobfit.matching.EvidenceRepository;
import com.jobfit.matching.MatchAnalysis;
import com.jobfit.matching.MatchAnalysisRepository;
import com.jobfit.resume.Resume;
import com.jobfit.resume.ResumeRepository;
import com.jobfit.resume.ResumeSkill;
import com.jobfit.resume.ResumeSkillRepository;
import com.jobfit.resume.SkillSource;
import com.jobfit.scoring.EvidenceStrength;
import com.jobfit.scoring.MatchType;
import com.jobfit.skill.Skill;
import com.jobfit.skill.SkillCategory;
import com.jobfit.skill.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * The dashboard is pure aggregation over other modules' data, so every test
 * here hand-builds a small, fully-controlled fixture (mocked repositories)
 * and hand-traces the expected aggregate numbers rather than asserting
 * against whatever the implementation happens to produce.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobRequirementRepository jobRequirementRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationStatusHistoryRepository historyRepository;
    @Mock private MatchAnalysisRepository matchAnalysisRepository;
    @Mock private EvidenceRepository evidenceRepository;
    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeSkillRepository resumeSkillRepository;
    @Mock private SkillRepository skillRepository;

    private DashboardService service;

    private static final Long USER_ID = 1L;

    // skills used across fixtures
    private Skill java, python, sql, git;

    @BeforeEach
    void setUp() throws Exception {
        service = new DashboardService(jobRepository, jobRequirementRepository, applicationRepository,
                historyRepository, matchAnalysisRepository, evidenceRepository, resumeRepository,
                resumeSkillRepository, skillRepository);

        java = newSkill(100L, "Java");
        python = newSkill(101L, "Python");
        sql = newSkill(102L, "SQL");
        git = newSkill(103L, "Git");

        // any findAllById call resolves against this fixed master set, filtered to the requested ids
        when(skillRepository.findAllById(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return List.of(java, python, sql, git).stream().filter(s -> ids.contains(s.getId())).toList();
        });
    }

    @Test
    void emptyState_whenUserHasNoJobsYet() {
        when(jobRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(applicationRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(resumeRepository.findByUserIdAndCurrentTrue(USER_ID)).thenReturn(Optional.empty());

        DashboardResponse response = service.getDashboard(USER_ID);

        assertThat(response.totalJobsAdded()).isZero();
        assertThat(response.jobsAnalysed()).isZero();
        assertThat(response.applicationsTracked()).isZero();
        assertThat(response.interviews()).isZero();
        assertThat(response.offers()).isZero();
        assertThat(response.averageFitScore()).isNull();
        assertThat(response.mostRequestedSkills()).isEmpty();
        assertThat(response.strongestSkills()).isEmpty();
        assertThat(response.commonSkillGaps()).isEmpty();
        assertThat(response.bestFitRoles()).isEmpty();
    }

    @Test
    void aggregatesAcrossJobsApplicationsAndAnalyses() throws Exception {
        // three jobs
        Job jobA = newJob(10L, "Backend Engineer", "Acme");
        Job jobB = newJob(11L, "Platform Engineer", "Beta Corp");
        Job jobC = newJob(12L, "Data Engineer", "Gamma");
        when(jobRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(jobA, jobB, jobC));

        // job A analysed twice (re-analysed after a resume edit) - latest (higher id/score) must win.
        // Mocked repo call is documented to return rows ordered by createdAt desc across ALL jobs,
        // so the re-analysis of job A is listed before the older one.
        MatchAnalysis jobAAnalysisNew = newMatchAnalysis(500L, 10L, 85);
        MatchAnalysis jobBAnalysis = newMatchAnalysis(501L, 11L, 60);
        MatchAnalysis jobAAnalysisOld = newMatchAnalysis(502L, 10L, 70);
        when(matchAnalysisRepository.findAllByJobIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of(jobAAnalysisNew, jobBAnalysis, jobAAnalysisOld));

        // job requirements: A requires Java + SQL, B requires Java + prefers Python, C requires Java
        JobRequirement reqAJava = newRequirement(200L, 10L, RequirementType.REQUIRED_SKILL, java.getId());
        JobRequirement reqASql = newRequirement(201L, 10L, RequirementType.REQUIRED_SKILL, sql.getId());
        JobRequirement reqBJava = newRequirement(202L, 11L, RequirementType.REQUIRED_SKILL, java.getId());
        JobRequirement reqBPython = newRequirement(203L, 11L, RequirementType.PREFERRED_SKILL, python.getId());
        JobRequirement reqCJava = newRequirement(204L, 12L, RequirementType.REQUIRED_SKILL, java.getId());
        when(jobRequirementRepository.findAllByJobIdInOrderByDisplayOrder(anyList()))
                .thenReturn(List.of(reqAJava, reqASql, reqBJava, reqBPython, reqCJava));

        // evidence: job A's latest analysis matched Java, missed SQL; job B's analysis missed Python
        Evidence evAJavaStrong = newEvidence(500L, reqAJava.getId(), MatchType.EXPLICIT, EvidenceStrength.STRONG);
        Evidence evASqlMissing = newEvidence(500L, reqASql.getId(), MatchType.ABSENT, EvidenceStrength.MISSING);
        Evidence evBJavaStrong = newEvidence(501L, reqBJava.getId(), MatchType.EXPLICIT, EvidenceStrength.STRONG);
        Evidence evBPythonMissing = newEvidence(501L, reqBPython.getId(), MatchType.ABSENT, EvidenceStrength.MISSING);
        when(evidenceRepository.findAllByMatchAnalysisIdIn(anyList()))
                .thenReturn(List.of(evAJavaStrong, evASqlMissing, evBJavaStrong, evBPythonMissing));

        // applications: one reached interview only, one reached offer, one never got past applied
        Application app1 = newApplication(300L, 10L);
        Application app2 = newApplication(301L, 11L);
        Application app3 = newApplication(302L, 12L);
        when(applicationRepository.findAllByUserId(USER_ID)).thenReturn(List.of(app1, app2, app3));
        when(historyRepository.findAllByApplicationIdIn(anyList())).thenReturn(List.of(
                new ApplicationStatusHistory(300L, ApplicationStatus.SAVED, null),
                new ApplicationStatusHistory(300L, ApplicationStatus.APPLIED, null),
                new ApplicationStatusHistory(300L, ApplicationStatus.INTERVIEW, null),
                new ApplicationStatusHistory(301L, ApplicationStatus.SAVED, null),
                new ApplicationStatusHistory(301L, ApplicationStatus.APPLIED, null),
                new ApplicationStatusHistory(301L, ApplicationStatus.REJECTED, null),
                new ApplicationStatusHistory(302L, ApplicationStatus.SAVED, null),
                new ApplicationStatusHistory(302L, ApplicationStatus.APPLIED, null),
                new ApplicationStatusHistory(302L, ApplicationStatus.INTERVIEW, null),
                new ApplicationStatusHistory(302L, ApplicationStatus.OFFER, null)
        ));

        // current resume explicitly lists Java and Git, and inferred Python (must NOT count as "strongest")
        Resume resume = newResume(400L);
        when(resumeRepository.findByUserIdAndCurrentTrue(USER_ID)).thenReturn(Optional.of(resume));
        when(resumeSkillRepository.findAllByResumeId(400L)).thenReturn(List.of(
                new ResumeSkill(400L, java.getId(), SkillSource.EXPLICIT, null),
                new ResumeSkill(400L, git.getId(), SkillSource.EXPLICIT, null),
                new ResumeSkill(400L, python.getId(), SkillSource.INFERRED, null)
        ));

        DashboardResponse response = service.getDashboard(USER_ID);

        assertThat(response.totalJobsAdded()).isEqualTo(3);
        assertThat(response.jobsAnalysed()).isEqualTo(2); // A and B; C never analysed
        assertThat(response.applicationsTracked()).isEqualTo(3);
        assertThat(response.interviews()).isEqualTo(2); // app1 (INTERVIEW) and app3 (OFFER implies past INTERVIEW)
        assertThat(response.offers()).isEqualTo(1); // app3 only

        // average of the LATEST analysis per job: (85 + 60) / 2 = 72.5 - the stale 70-point
        // re-analysis of job A must not be double-counted or override the newer 85.
        assertThat(response.averageFitScore()).isEqualTo(72.5);

        assertThat(response.mostRequestedSkills()).hasSize(3);
        assertThat(response.mostRequestedSkills().get(0).skillName()).isEqualTo("Java");
        assertThat(response.mostRequestedSkills().get(0).count()).isEqualTo(3); // required by A, B, C

        assertThat(response.commonSkillGaps()).hasSize(2);
        assertThat(response.commonSkillGaps()).extracting("skillName").containsExactlyInAnyOrder("SQL", "Python");

        assertThat(response.strongestSkills()).containsExactly("Git", "Java"); // alphabetical, explicit only

        assertThat(response.bestFitRoles()).hasSize(2);
        assertThat(response.bestFitRoles().get(0).jobId()).isEqualTo(10L);
        assertThat(response.bestFitRoles().get(0).score()).isEqualTo(85);
        assertThat(response.bestFitRoles().get(1).jobId()).isEqualTo(11L);
        assertThat(response.bestFitRoles().get(1).score()).isEqualTo(60);
    }

    @Test
    void noResumeYet_strongestSkillsIsEmptyButRestOfDashboardStillWorks() throws Exception {
        Job job = newJob(10L, "Backend Engineer", "Acme");
        when(jobRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(job));
        when(applicationRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(matchAnalysisRepository.findAllByJobIdInOrderByCreatedAtDesc(anyList())).thenReturn(List.of());
        when(jobRequirementRepository.findAllByJobIdInOrderByDisplayOrder(anyList())).thenReturn(List.of());
        when(resumeRepository.findByUserIdAndCurrentTrue(USER_ID)).thenReturn(Optional.empty());

        DashboardResponse response = service.getDashboard(USER_ID);

        assertThat(response.totalJobsAdded()).isEqualTo(1);
        assertThat(response.jobsAnalysed()).isZero();
        assertThat(response.averageFitScore()).isNull();
        assertThat(response.strongestSkills()).isEmpty();
        assertThat(response.bestFitRoles()).isEmpty();
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

    private static MatchAnalysis newMatchAnalysis(Long id, Long jobId, int score) throws Exception {
        MatchAnalysis analysis = new MatchAnalysis(400L, jobId, score, "STRONG_MATCH", "reason");
        setId(analysis, id);
        return analysis;
    }

    private static JobRequirement newRequirement(Long id, Long jobId, RequirementType type, Long skillId) throws Exception {
        JobRequirement requirement = new JobRequirement(jobId, type, "raw text", skillId, 0);
        setId(requirement, id);
        return requirement;
    }

    private static Evidence newEvidence(Long matchAnalysisId, Long jobRequirementId, MatchType matchType,
                                         EvidenceStrength strength) throws Exception {
        Evidence evidence = new Evidence(matchAnalysisId, jobRequirementId, matchType, strength,
                null, null, "explanation", null);
        setId(evidence, matchAnalysisId * 1000 + jobRequirementId);
        return evidence;
    }

    private static Application newApplication(Long id, Long jobId) throws Exception {
        Application application = new Application(USER_ID, jobId, null, null);
        setId(application, id);
        return application;
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
