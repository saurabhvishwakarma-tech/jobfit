package com.jobfit.matching;

import com.jobfit.scoring.EvidenceStrength;
import com.jobfit.scoring.MatchType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "evidence")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_analysis_id", nullable = false)
    private Long matchAnalysisId;

    @Column(name = "job_requirement_id", nullable = false)
    private Long jobRequirementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private MatchType matchType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceStrength strength;

    @Column(name = "resume_ref_type", length = 30)
    private String resumeRefType;

    @Column(name = "resume_ref_id")
    private Long resumeRefId;

    @Column(name = "explanation_text", nullable = false, length = 500)
    private String explanationText;

    private BigDecimal confidence;

    public Evidence(Long matchAnalysisId, Long jobRequirementId, MatchType matchType, EvidenceStrength strength,
                     String resumeRefType, Long resumeRefId, String explanationText, BigDecimal confidence) {
        this.matchAnalysisId = matchAnalysisId;
        this.jobRequirementId = jobRequirementId;
        this.matchType = matchType;
        this.strength = strength;
        this.resumeRefType = resumeRefType;
        this.resumeRefId = resumeRefId;
        this.explanationText = explanationText;
        this.confidence = confidence;
    }
}
