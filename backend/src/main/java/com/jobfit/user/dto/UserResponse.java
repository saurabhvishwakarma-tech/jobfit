package com.jobfit.user.dto;

import com.jobfit.user.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }
}
