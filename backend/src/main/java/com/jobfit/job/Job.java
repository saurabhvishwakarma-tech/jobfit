package com.jobfit.job;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    private String company;

    @Column(name = "raw_description", nullable = false, columnDefinition = "text")
    private String rawDescription;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 20)
    private ParseStatus parseStatus = ParseStatus.PENDING;

    @Column(name = "parse_error", length = 1000)
    private String parseError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    public Job(Long userId, String title, String company, String rawDescription, String sourceUrl) {
        this.userId = userId;
        this.title = title;
        this.company = company;
        this.rawDescription = rawDescription;
        this.sourceUrl = sourceUrl;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void markProcessing() {
        this.parseStatus = ParseStatus.PROCESSING;
    }

    public void markReady() {
        this.parseStatus = ParseStatus.READY;
        this.parsedAt = Instant.now();
        this.parseError = null;
    }

    public void markFailed(String errorMessage) {
        this.parseStatus = ParseStatus.FAILED;
        this.parsedAt = Instant.now();
        this.parseError = errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 1000) : errorMessage;
    }
}
