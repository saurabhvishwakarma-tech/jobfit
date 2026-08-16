package com.jobfit.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables @Async methods (used by the resume/job parsing pipelines to run
 * off the request thread - see docs/JobFit_Design_v1.md, System
 * Architecture: no message broker, just @Async + a status column the
 * frontend polls).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
