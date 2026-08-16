package com.jobfit.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Job> findByIdAndUserId(Long id, Long userId);
    List<Job> findAllByIdInAndUserId(List<Long> ids, Long userId);
}
