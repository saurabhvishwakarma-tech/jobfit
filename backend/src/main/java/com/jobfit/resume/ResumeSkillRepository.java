package com.jobfit.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeSkillRepository extends JpaRepository<ResumeSkill, Long> {
    List<ResumeSkill> findAllByResumeId(Long resumeId);
    boolean existsByResumeIdAndSkillId(Long resumeId, Long skillId);
}
