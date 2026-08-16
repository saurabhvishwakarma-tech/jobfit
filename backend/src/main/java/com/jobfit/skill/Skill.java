package com.jobfit.skill;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The shared skill taxonomy used to normalize both resume skills and job
 * requirements onto the same vocabulary (see docs/JobFit_Design_v1.md,
 * Matching Algorithm step 1). Lives in its own top-level module because
 * both `resume` and `job` depend on it - putting it inside either would
 * create a circular module dependency.
 */
@Entity
@Table(name = "skills")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_name", nullable = false, unique = true, length = 150)
    private String canonicalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SkillCategory category;

    public Skill(String canonicalName, SkillCategory category) {
        this.canonicalName = canonicalName;
        this.category = category;
    }
}
