package com.jobfit.resumeats.dto;

import java.util.List;

public record AtsScoreResponse(Long resumeId, int score, List<AtsCheckDto> checks) {
}
