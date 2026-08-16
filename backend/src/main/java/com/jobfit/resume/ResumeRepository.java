package com.jobfit.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findAllByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    Optional<Resume> findByUserIdAndCurrentTrue(Long userId);

    @Modifying
    @Query("update Resume r set r.current = false where r.userId = :userId and r.current = true")
    void clearCurrentFlagForUser(@Param("userId") Long userId);
}
