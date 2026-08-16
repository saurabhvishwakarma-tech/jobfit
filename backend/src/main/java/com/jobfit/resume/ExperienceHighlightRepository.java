package com.jobfit.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceHighlightRepository extends JpaRepository<ExperienceHighlight, Long> {
    List<ExperienceHighlight> findAllByExperienceIdOrderByDisplayOrder(Long experienceId);
    List<ExperienceHighlight> findAllByExperienceIdIn(List<Long> experienceIds);
}
