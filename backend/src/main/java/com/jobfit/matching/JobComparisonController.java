package com.jobfit.matching;

import com.jobfit.common.util.SecurityUtils;
import com.jobfit.matching.dto.JobComparisonResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Matching", description = "Analyse a resume against a job and review the scored evidence")
public class JobComparisonController {

    private final JobComparisonService jobComparisonService;

    public JobComparisonController(JobComparisonService jobComparisonService) {
        this.jobComparisonService = jobComparisonService;
    }

    @GetMapping("/api/jobs/compare")
    public JobComparisonResponse compare(@RequestParam List<Long> jobIds) {
        return jobComparisonService.compare(SecurityUtils.currentUserId(), jobIds);
    }
}
