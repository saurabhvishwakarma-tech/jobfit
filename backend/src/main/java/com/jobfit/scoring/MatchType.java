package com.jobfit.scoring;

/**
 * How a piece of resume evidence relates to a job requirement.
 * EXPLICIT  - the exact skill/term (or a known alias) is present in the resume.
 * INFERRED  - a related-but-not-identical signal was found (e.g. lexical
 *             overlap between a JD responsibility and a resume bullet, or
 *             an AI-proposed skill equivalence); never conflated with EXPLICIT.
 * ABSENT    - no evidence was found at all.
 * See docs/JobFit_Design_v1.md, "Evidence Is Critical".
 */
public enum MatchType {
    EXPLICIT, INFERRED, ABSENT
}
