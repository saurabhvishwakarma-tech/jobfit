package com.jobfit.user.dto;

public record AuthResponse(
        String accessToken,
        long accessTokenExpiresInSeconds,
        String refreshToken,
        UserResponse user
) {
}
