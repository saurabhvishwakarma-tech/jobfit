package com.jobfit.skill;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skill_aliases")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String alias;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    public SkillAlias(String alias, Long skillId) {
        this.alias = alias;
        this.skillId = skillId;
    }
}
