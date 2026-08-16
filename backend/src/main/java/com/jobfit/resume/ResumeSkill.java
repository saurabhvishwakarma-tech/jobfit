package com.jobfit.resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A skill tagged against a resume. EXPLICIT means the exact term (or a
 * known alias) appeared in the resume text. INFERRED means an AI-assisted
 * equivalence step proposed it from related but non-identical wording -
 * always shown distinctly in the UI, never conflated with an explicit
 * match (see docs/JobFit_Design_v1.md, "Evidence Is Critical").
 */
@Entity
@Table(name = "resume_skills")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillSource source;

    @Column(name = "evidence_highlight_id")
    private Long evidenceHighlightId;

    private java.math.BigDecimal confidence;

    public ResumeSkill(Long resumeId, Long skillId, SkillSource source, Long evidenceHighlightId) {
        this.resumeId = resumeId;
        this.skillId = skillId;
        this.source = source;
        this.evidenceHighlightId = evidenceHighlightId;
    }
}
