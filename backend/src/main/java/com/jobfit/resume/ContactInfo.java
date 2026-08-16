package com.jobfit.resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contact_infos")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false, unique = true)
    private Long resumeId;

    @Column(name = "full_name")
    private String fullName;

    private String email;
    private String phone;
    private String location;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    public ContactInfo(Long resumeId) {
        this.resumeId = resumeId;
    }
}
