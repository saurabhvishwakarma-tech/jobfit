package com.jobfit.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(prefix = "jobfit.storage", name = "provider", havingValue = "s3")
public class S3ClientConfig {

    @Bean
    public S3Client s3Client() {
        // Credentials/region resolved from the default AWS provider chain
        // (env vars, IAM role, etc.) - never hardcoded here.
        return S3Client.builder().build();
    }
}
