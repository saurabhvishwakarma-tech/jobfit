package com.jobfit.matching;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "score_components")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_analysis_id", nullable = false)
    private Long matchAnalysisId;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(name = "max_points", nullable = false)
    private BigDecimal maxPoints;

    @Column(name = "earned_points", nullable = false)
    private BigDecimal earnedPoints;

    @Column(nullable = false, length = 500)
    private String explanation;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public ScoreComponent(Long matchAnalysisId, String category, BigDecimal maxPoints, BigDecimal earnedPoints,
                           String explanation, int displayOrder) {
        this.matchAnalysisId = matchAnalysisId;
        this.category = category;
        this.maxPoints = maxPoints;
        this.earnedPoints = earnedPoints;
        this.explanation = explanation;
        this.displayOrder = displayOrder;
    }
}
