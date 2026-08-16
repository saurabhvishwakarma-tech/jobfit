package com.jobfit.ai;

import java.util.List;

/**
 * Single abstraction point for every call out to a language model,
 * anywhere in the application. Nothing outside this package (and its
 * implementations) should know or care which provider is behind it -
 * that's what keeps the architecture provider-independent and testable
 * (service-layer tests inject a fake implementation instead of hitting a
 * real API; see docs/JobFit_Design_v1.md, "Where AI Should/Should Not Be
 * Used").
 *
 * Every method here is additive, best-effort enhancement on top of a
 * deterministic pipeline - callers must be able to function correctly
 * (just less richly) if isAvailable() is false or a call throws.
 */
public interface AiClient {

    boolean isAvailable();

    /**
     * Given raw resume text and the skill terms already found by
     * deterministic keyword matching, suggests additional skill terms that
     * might be present but phrased ambiguously (e.g. "built services with
     * Spring's dependency injection" implying Spring Boot). Callers must
     * treat every returned term as INFERRED, never EXPLICIT.
     */
    List<String> suggestAdditionalResumeSkills(String rawResumeText, List<String> alreadyIdentifiedSkills);
}
