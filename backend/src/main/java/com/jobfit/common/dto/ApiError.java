package com.jobfit.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Consistent error response shape returned by GlobalExceptionHandler for
 * every error case in the API.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError withFieldErrors(int status, String error, String message, String path,
                                            List<FieldViolation> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }

    public static ApiError fromMap(int status, String error, String message, String path,
                                    Map<String, String> fields) {
        List<FieldViolation> violations = fields.entrySet().stream()
                .map(e -> new FieldViolation(e.getKey(), e.getValue()))
                .toList();
        return withFieldErrors(status, error, message, path, violations);
    }
}
