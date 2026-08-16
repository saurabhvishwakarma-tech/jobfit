package com.jobfit.resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Comma-separated for simplicity - parsed/rendered as tags in the UI. */
    private String technologies;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public ProjectEntry(Long resumeId, String name) {
        this.resumeId = resumeId;
        this.name = name;
    }
}
