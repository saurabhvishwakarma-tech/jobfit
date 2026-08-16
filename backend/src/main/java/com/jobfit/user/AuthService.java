package com.jobfit.user;

import com.jobfit.common.exception.DuplicateResourceException;
import com.jobfit.common.exception.InvalidTokenException;
import com.jobfit.common.security.JwtService;
import com.jobfit.user.dto.AuthResponse;
import com.jobfit.user.dto.LoginRequest;
import com.jobfit.user.dto.RegisterRequest;
import com.jobfit.user.dto.UserResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()), request.fullName().trim());
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        // Delegates to DaoAuthenticationProvider -> throws BadCredentialsException on failure,
        // which GlobalExceptionHandler maps to a generic 401 (no user-enumeration hints).
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished mid-request"));
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid or has been revoked"));

        if (!stored.isValid()) {
            throw new InvalidTokenException("Refresh token is invalid or has expired");
        }

        // Rotate: revoke the used token and issue a brand new pair. This limits the blast
        // radius if a refresh token is ever stolen - it can only be replayed once before
        // both client and attacker discover it has been invalidated.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User no longer exists"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

        String rawRefreshToken = jwtService.generateRefreshTokenValue();
        RefreshToken refreshToken = new RefreshToken(
                user.getId(), jwtService.hashToken(rawRefreshToken), jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                jwtService.accessTokenTtlSeconds(),
                rawRefreshToken,
                UserResponse.from(user));
    }
}
