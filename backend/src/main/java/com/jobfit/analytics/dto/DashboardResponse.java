package com.jobfit.analytics.dto;

import java.util.List;

/**
 * Aggregate view over a user's own jobs, resumes, match analyses and
 * applications. Everything here is read-only - the dashboard doesn't own
 * any data, it just summarizes what the other modules already computed
 * (see docs/JobFit_Design_v1.md, Dashboard requirements). No new AI or
 * scoring happens here.
 */
public record DashboardResponse(
        int totalJobsAdded,
        int jobsAnalysed,
        int applicationsTracked,
        int interviews,
        int offers,
        Double averageFitScore,
        List<SkillFrequencyDto> mostRequestedSkills,
        List<String> strongestSkills,
        List<SkillFrequencyDto> commonSkillGaps,
        List<BestFitRoleDto> bestFitRoles) {
}
