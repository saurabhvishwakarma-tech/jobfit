package com.jobfit.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    List<Evidence> findAllByMatchAnalysisId(Long matchAnalysisId);
    List<Evidence> findAllByMatchAnalysisIdIn(List<Long> matchAnalysisIds);
}
