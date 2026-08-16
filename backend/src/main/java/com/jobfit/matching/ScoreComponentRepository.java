package com.jobfit.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreComponentRepository extends JpaRepository<ScoreComponent, Long> {
    List<ScoreComponent> findAllByMatchAnalysisIdOrderByDisplayOrder(Long matchAnalysisId);
    List<ScoreComponent> findAllByMatchAnalysisIdInOrderByDisplayOrder(List<Long> matchAnalysisIds);
}
