package com.jobfit.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificationRepository extends JpaRepository<Certification, Long> {
    List<Certification> findAllByResumeIdOrderByDisplayOrder(Long resumeId);
}
