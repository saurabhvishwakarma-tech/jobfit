package com.jobfit.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {
    List<ApplicationStatusHistory> findAllByApplicationIdOrderByChangedAtAsc(Long applicationId);
    List<ApplicationStatusHistory> findAllByApplicationIdIn(List<Long> applicationIds);
}
