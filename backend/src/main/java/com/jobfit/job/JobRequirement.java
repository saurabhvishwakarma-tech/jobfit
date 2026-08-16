package com.jobfit.job;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "job_requirements")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementType type;

    @Column(name = "raw_text", nullable = false, length = 1000)
    private String rawText;

    @Column(name = "normalized_skill_id")
    private Long normalizedSkillId;

    private BigDecimal weight;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public JobRequirement(Long jobId, RequirementType type, String rawText, Long normalizedSkillId, int displayOrder) {
        this.jobId = jobId;
        this.type = type;
        this.rawText = rawText;
        this.normalizedSkillId = normalizedSkillId;
        this.displayOrder = displayOrder;
    }
}
