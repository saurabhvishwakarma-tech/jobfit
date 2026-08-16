package com.jobfit.resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "certifications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(nullable = false)
    private String name;

    private String issuer;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Certification(Long resumeId, String name) {
        this.resumeId = resumeId;
        this.name = name;
    }
}
