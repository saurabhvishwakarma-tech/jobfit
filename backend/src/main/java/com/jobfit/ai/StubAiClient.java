package com.jobfit.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default, zero-configuration AiClient: makes no network calls and returns
 * no suggestions. This is intentional, not a placeholder to be embarrassed
 * about - the application must be fully usable (deterministic parsing,
 * deterministic scoring) with no AI provider configured at all, per the
 * "AI is not the product" principle. Swap in a real provider by setting
 * jobfit.ai.provider and adding its adapter.
 */
@Service
@ConditionalOnProperty(prefix = "jobfit.ai", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubAiClient implements AiClient {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<String> suggestAdditionalResumeSkills(String rawResumeText, List<String> alreadyIdentifiedSkills) {
        return List.of();
    }
}
