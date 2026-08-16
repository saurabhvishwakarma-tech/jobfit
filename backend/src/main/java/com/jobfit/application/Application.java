package com.jobfit.application;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "match_analysis_id")
    private Long matchAnalysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.SAVED;

    @Column(length = 4000)
    private String notes;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Application(Long userId, Long jobId, Long resumeId, Long matchAnalysisId) {
        this.userId = userId;
        this.jobId = jobId;
        this.resumeId = resumeId;
        this.matchAnalysisId = matchAnalysisId;
        this.status = ApplicationStatus.SAVED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void changeStatus(ApplicationStatus newStatus) {
        this.status = newStatus;
        if (newStatus == ApplicationStatus.APPLIED && this.appliedAt == null) {
            this.appliedAt = Instant.now();
        }
    }
}
