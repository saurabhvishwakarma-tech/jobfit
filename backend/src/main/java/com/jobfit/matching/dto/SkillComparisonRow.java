package com.jobfit.matching.dto;

import java.util.List;

/**
 * One row of the skill-overlap table. `requirementPerJob` is positionally
 * aligned with `JobComparisonResponse.jobs()` - index i is this skill's
 * requirement type ("REQUIRED", "PREFERRED", or null if not mentioned) for
 * jobs().get(i). `resumeStatus` is EXPLICIT / INFERRED / ABSENT against the
 * user's current resume - never collapsed into a single yes/no, per the
 * evidence-based design principle (see docs/JobFit_Design_v1.md).
 */
public record SkillComparisonRow(String skillName, List<String> requirementPerJob, String resumeStatus) {
}
