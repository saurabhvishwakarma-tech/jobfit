package com.jobfit.resumequality;

import com.jobfit.common.util.SecurityUtils;
import com.jobfit.resumequality.dto.ResumeQualityResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Resume Quality", description = "Standalone rule-based writing linter, independent of any job")
public class ResumeQualityController {

    private final ResumeQualityService resumeQualityService;

    public ResumeQualityController(ResumeQualityService resumeQualityService) {
        this.resumeQualityService = resumeQualityService;
    }

    @GetMapping("/api/resumes/{id}/quality")
    public ResumeQualityResponse getQuality(@PathVariable Long id) {
        return resumeQualityService.analyze(SecurityUtils.currentUserId(), id);
    }
}
