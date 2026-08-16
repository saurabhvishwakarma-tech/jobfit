package com.jobfit.resume.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev/local-only implementation - stores resume files on disk under
 * jobfit.storage.local-base-path, namespaced per user so one user's files
 * are never in a directory another user could enumerate. Not suitable for
 * production (no encryption at rest, no durability across deploys) - see
 * S3ResumeStorageService for that.
 */
@Service
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(prefix = "jobfit.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalResumeStorageService implements ResumeStorageService {

    private final Path basePath;

    public LocalResumeStorageService(StorageProperties properties) {
        // Must be absolute + normalized up front: resolveSafe() compares this
        // against an already-normalized target path, and a relative path like
        // "./data/resumes" still carries a literal "." segment that never
        // equals its normalized form, so startsWith() would always fail and
        // reject every upload.
        this.basePath = Path.of(properties.localBasePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create local resume storage directory", e);
        }
    }

    @Override
    public String store(Long userId, Long resumeId, String originalFilename, byte[] content) {
        String storageKey = "user-%d/resume-%d.pdf".formatted(userId, resumeId);
        Path target = resolveSafe(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write resume file to local storage", e);
        }
        return storageKey;
    }

    @Override
    public byte[] retrieve(String storageKey) {
        try {
            return Files.readAllBytes(resolveSafe(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read resume file from local storage", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete resume file from local storage", e);
        }
    }

    /** Resolves a storage key to a path guaranteed to stay under basePath (path-traversal guard). */
    private Path resolveSafe(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
