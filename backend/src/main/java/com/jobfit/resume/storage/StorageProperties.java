package com.jobfit.resume.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobfit.storage")
public record StorageProperties(
        String provider,
        String localBasePath,
        String s3Bucket
) {
}
