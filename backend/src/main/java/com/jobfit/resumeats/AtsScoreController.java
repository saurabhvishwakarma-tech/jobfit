package com.jobfit.resumeats;

import com.jobfit.common.util.SecurityUtils;
import com.jobfit.resumeats.dto.AtsScoreResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "ATS Score", description = "How reliably an Applicant Tracking System is likely to parse this resume")
public class AtsScoreController {

    private final AtsScoreService atsScoreService;

    public AtsScoreController(AtsScoreService atsScoreService) {
        this.atsScoreService = atsScoreService;
    }

    @GetMapping("/api/resumes/{id}/ats-score")
    public AtsScoreResponse getAtsScore(@PathVariable Long id) {
        return atsScoreService.analyze(SecurityUtils.currentUserId(), id);
    }
}
