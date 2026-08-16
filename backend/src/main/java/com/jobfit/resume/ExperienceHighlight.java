package com.jobfit.resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per resume bullet point. This is what makes evidence linking
 * (Requirement -> exact resume sentence) possible in the matching engine -
 * without bullet-level granularity you can only point at a whole
 * experience block, which isn't a useful citation.
 */
@Entity
@Table(name = "experience_highlights")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperienceHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "experience_id", nullable = false)
    private Long experienceId;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public ExperienceHighlight(Long experienceId, String text, int displayOrder) {
        this.experienceId = experienceId;
        this.text = text;
        this.displayOrder = displayOrder;
    }
}
