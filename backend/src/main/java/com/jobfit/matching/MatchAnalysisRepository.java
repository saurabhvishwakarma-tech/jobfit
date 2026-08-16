package com.jobfit.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchAnalysisRepository extends JpaRepository<MatchAnalysis, Long> {
    List<MatchAnalysis> findAllByResumeIdOrderByCreatedAtDesc(Long resumeId);
    Optional<MatchAnalysis> findFirstByResumeIdAndJobIdOrderByCreatedAtDesc(Long resumeId, Long jobId);
    List<MatchAnalysis> findAllByJobIdInOrderByCreatedAtDesc(List<Long> jobIds);
}
