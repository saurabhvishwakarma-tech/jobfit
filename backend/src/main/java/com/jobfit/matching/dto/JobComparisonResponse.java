package com.jobfit.matching.dto;

import java.util.List;

public record JobComparisonResponse(List<ComparedJobDto> jobs, List<SkillComparisonRow> skillComparison) {
}
