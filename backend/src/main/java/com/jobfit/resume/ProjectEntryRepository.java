package com.jobfit.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectEntryRepository extends JpaRepository<ProjectEntry, Long> {
    List<ProjectEntry> findAllByResumeIdOrderByDisplayOrder(Long resumeId);
}
