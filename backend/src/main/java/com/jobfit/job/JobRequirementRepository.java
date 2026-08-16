package com.jobfit.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {
    List<JobRequirement> findAllByJobIdOrderByDisplayOrder(Long jobId);
    List<JobRequirement> findAllByJobIdInOrderByDisplayOrder(List<Long> jobIds);
}
