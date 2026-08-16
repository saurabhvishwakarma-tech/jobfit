package com.jobfit.matching;

import com.jobfit.common.util.SecurityUtils;
import com.jobfit.matching.dto.AnalyseRequest;
import com.jobfit.matching.dto.MatchAnalysisDetailResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Matching", description = "Analyse a resume against a job and review the scored evidence")
public class MatchAnalysisController {

    private final MatchAnalysisService matchAnalysisService;

    public MatchAnalysisController(MatchAnalysisService matchAnalysisService) {
        this.matchAnalysisService = matchAnalysisService;
    }

    @PostMapping("/api/jobs/{jobId}/analyse")
    public MatchAnalysisDetailResponse analyse(@PathVariable Long jobId,
                                                @RequestBody(required = false) AnalyseRequest request) {
        Long resumeId = request == null ? null : request.resumeId();
        return matchAnalysisService.analyse(SecurityUtils.currentUserId(), jobId, resumeId);
    }

    @GetMapping("/api/match-analyses/{id}")
    public MatchAnalysisDetailResponse get(@PathVariable Long id) {
        return matchAnalysisService.getDetail(SecurityUtils.currentUserId(), id);
    }
}
