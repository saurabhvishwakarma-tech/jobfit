package com.jobfit.job;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.job.dto.JobCreateRequest;
import com.jobfit.skill.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobRequirementRepository requirementRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private JobParsingCoordinator parsingCoordinator;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, requirementRepository, skillRepository, parsingCoordinator);
    }

    @Test
    void create_savesJobAndTriggersAsyncParse() {
        JobCreateRequest request = new JobCreateRequest("Backend Engineer", "Acme", "Requirements\n- Java", null);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job j = invocation.getArgument(0);
            setId(j, 55L);
            return j;
        });

        var response = jobService.create(1L, request);

        assertThat(response.id()).isEqualTo(55L);
        assertThat(response.title()).isEqualTo("Backend Engineer");
        verify(parsingCoordinator).parseAsync(55L);
    }

    @Test
    void delete_throwsNotFound_whenJobDoesNotBelongToUser() {
        when(jobRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.delete(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(jobRepository, never()).delete(any());
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
