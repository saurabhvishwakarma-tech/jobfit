package com.jobfit.resume.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Production storage backend. Objects are server-side encrypted (SSE-S3)
 * and keyed per-user so a presigned URL or IAM policy scoped to a prefix
 * can enforce isolation between users' resumes at the storage layer, not
 * just in application code.
 */
@Service
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(prefix = "jobfit.storage", name = "provider", havingValue = "s3")
public class S3ResumeStorageService implements ResumeStorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3ResumeStorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.s3Bucket();
    }

    @Override
    public String store(Long userId, Long resumeId, String originalFilename, byte[] content) {
        String key = "user-%d/resume-%d.pdf".formatted(userId, resumeId);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/pdf")
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromBytes(content));
        return key;
    }

    @Override
    public byte[] retrieve(String storageKey) {
        ResponseBytes<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(storageKey).build(),
                software.amazon.awssdk.core.sync.ResponseTransformer.toBytes());
        return response.asByteArray();
    }

    @Override
    public void delete(String storageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
    }
}
