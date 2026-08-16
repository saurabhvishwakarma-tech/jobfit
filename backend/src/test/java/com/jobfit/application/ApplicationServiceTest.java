package com.jobfit.application;

import com.jobfit.application.dto.ApplicationCreateRequest;
import com.jobfit.application.dto.ApplicationStatusUpdateRequest;
import com.jobfit.common.exception.DuplicateResourceException;
import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.job.Job;
import com.jobfit.job.JobRepository;
import com.jobfit.matching.MatchAnalysisRepository;
import com.jobfit.resume.ResumeRepository;
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
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationStatusHistoryRepository historyRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ResumeRepository resumeRepository;
    @Mock private MatchAnalysisRepository matchAnalysisRepository;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, historyRepository, jobRepository,
                resumeRepository, matchAnalysisRepository);
    }

    @Test
    void create_throwsNotFound_whenJobDoesNotBelongToUser() {
        when(jobRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new ApplicationCreateRequest(5L, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_throwsDuplicate_whenApplicationAlreadyTrackedForJob() throws Exception {
        Job job = newJob(5L);
        when(jobRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(job));
        when(applicationRepository.findByUserIdAndJobId(1L, 5L))
                .thenReturn(Optional.of(new Application(1L, 5L, null, null)));

        assertThatThrownBy(() -> service.create(1L, new ApplicationCreateRequest(5L, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateStatus_throwsIllegalArgument_forUnknownStatus() throws Exception {
        Application application = new Application(1L, 5L, null, null);
        setId(application, 10L);
        when(applicationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.updateStatus(1L, 10L, new ApplicationStatusUpdateRequest("NOT_A_STATUS", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown application status");
    }

    @Test
    void delete_throwsNotFound_whenApplicationNotOwnedByUser() {
        when(applicationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Job newJob(Long id) throws Exception {
        Constructor<Job> ctor = Job.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Job job = ctor.newInstance();
        setId(job, id);
        return job;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
