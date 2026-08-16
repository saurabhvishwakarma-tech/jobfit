package com.jobfit.job;

import com.jobfit.common.util.SecurityUtils;
import com.jobfit.job.dto.JobCreateRequest;
import com.jobfit.job.dto.JobDetailResponse;
import com.jobfit.job.dto.JobSummaryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Add job postings and review their structured requirements")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobSummaryResponse create(@Valid @RequestBody JobCreateRequest request) {
        return jobService.create(SecurityUtils.currentUserId(), request);
    }

    @GetMapping
    public List<JobSummaryResponse> list() {
        return jobService.list(SecurityUtils.currentUserId());
    }

    @GetMapping("/{id}")
    public JobDetailResponse get(@PathVariable Long id) {
        return jobService.getDetail(SecurityUtils.currentUserId(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jobService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
