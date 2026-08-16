package com.jobfit.resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Each upload is its own immutable(-ish) versioned row rather than a
 * separate ResumeVersion table pointing at a "logical resume" - simpler,
 * and it means every MatchAnalysis/Application can pin the exact resume
 * snapshot that was used just by storing a resume_id.
 */
@Entity
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 20)
    private ParseStatus parseStatus = ParseStatus.PENDING;

    @Column(name = "parse_error", length = 1000)
    private String parseError;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    public Resume(Long userId, int versionNo, String originalFilename, String storageKey) {
        this.userId = userId;
        this.versionNo = versionNo;
        this.current = true;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.parseStatus = ParseStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.uploadedAt = Instant.now();
    }

    public void markProcessing() {
        this.parseStatus = ParseStatus.PROCESSING;
    }

    public void markReady(String rawText) {
        this.rawText = rawText;
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
