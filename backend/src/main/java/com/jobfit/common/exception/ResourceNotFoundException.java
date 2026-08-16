package com.jobfit.common.exception;

/**
 * Thrown when a requested resource does not exist, OR when it exists but does
 * not belong to the current user. We deliberately do not distinguish between
 * "not found" and "not yours" at the HTTP layer (both return 404) to avoid
 * leaking the existence of other users' resources (IDOR mitigation).
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " with id " + id + " not found");
    }
}
