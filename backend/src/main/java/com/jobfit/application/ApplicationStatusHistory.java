package com.jobfit.application;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "application_status_history")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(length = 1000)
    private String notes;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public ApplicationStatusHistory(Long applicationId, ApplicationStatus status, String notes) {
        this.applicationId = applicationId;
        this.status = status;
        this.notes = notes;
    }

    @PrePersist
    void onCreate() {
        this.changedAt = Instant.now();
    }
}
