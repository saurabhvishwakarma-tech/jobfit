package com.jobfit.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTest {

    @Test
    void changeStatus_setsAppliedAt_onFirstTransitionToApplied() {
        Application application = new Application(1L, 2L, 3L, null);
        assertThat(application.getAppliedAt()).isNull();

        application.changeStatus(ApplicationStatus.APPLIED);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(application.getAppliedAt()).isNotNull();
    }

    @Test
    void changeStatus_doesNotOverwriteAppliedAt_onSubsequentTransitions() throws InterruptedException {
        Application application = new Application(1L, 2L, 3L, null);
        application.changeStatus(ApplicationStatus.APPLIED);
        var firstAppliedAt = application.getAppliedAt();

        Thread.sleep(5);
        application.changeStatus(ApplicationStatus.INTERVIEW);
        application.changeStatus(ApplicationStatus.APPLIED);

        assertThat(application.getAppliedAt()).isEqualTo(firstAppliedAt);
    }

    @Test
    void changeStatus_doesNotSetAppliedAt_forNonAppliedStatuses() {
        Application application = new Application(1L, 2L, 3L, null);

        application.changeStatus(ApplicationStatus.INTERVIEW);

        assertThat(application.getAppliedAt()).isNull();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
    }
}
