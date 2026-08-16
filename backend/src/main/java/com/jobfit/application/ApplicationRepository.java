package com.jobfit.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findAllByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<Application> findByIdAndUserId(Long id, Long userId);
    Optional<Application> findByUserIdAndJobId(Long userId, Long jobId);
    long countByUserIdAndStatus(Long userId, ApplicationStatus status);
    List<Application> findAllByUserId(Long userId);
}
