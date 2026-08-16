package com.jobfit.resume.storage;

/**
 * Abstraction over where resume files physically live. Resume files are
 * NEVER stored in the database - only a storageKey pointer is. This
 * interface lets local disk (dev) and S3 (prod) be swapped via config
 * (jobfit.storage.provider) without touching any calling code.
 */
public interface ResumeStorageService {

    /** Stores the file content and returns an opaque storage key to persist on the Resume row. */
    String store(Long userId, Long resumeId, String originalFilename, byte[] content);

    byte[] retrieve(String storageKey);

    void delete(String storageKey);
}
