package com.jobfit.matching;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "match_analyses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(nullable = false, length = 30)
    private String recommendation;

    @Column(name = "recommendation_reason", nullable = false, length = 2000)
    private String recommendationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MatchAnalysis(Long resumeId, Long jobId, int overallScore, String recommendation,
                          String recommendationReason) {
        this.resumeId = resumeId;
        this.jobId = jobId;
        this.overallScore = overallScore;
        this.recommendation = recommendation;
        this.recommendationReason = recommendationReason;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
