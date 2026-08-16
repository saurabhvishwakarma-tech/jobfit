package com.jobfit.user;

import com.jobfit.common.exception.DuplicateResourceException;
import com.jobfit.common.exception.InvalidTokenException;
import com.jobfit.common.security.JwtService;
import com.jobfit.user.dto.AuthResponse;
import com.jobfit.user.dto.LoginRequest;
import com.jobfit.user.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for AuthService business logic. All collaborators are
 * mocked - no Spring context, no database - so this suite runs in
 * milliseconds and exercises the actual decision logic (duplicate email
 * rejection, refresh token rotation/revocation, expiry checks).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void register_createsUser_whenEmailNotTaken() {
        RegisterRequest request = new RegisterRequest("Jane.Doe@Example.com", "supersecret1", "Jane Doe");
        when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("supersecret1")).thenReturn("hashed-password");
        when(jwtService.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshTokenValue()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
        // Simulate JPA assigning an id on save.
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            setId(u, 42L);
            return u;
        }).when(userRepository).save(any(User.class));

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.user().email()).isEqualTo("jane.doe@example.com");

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void register_throwsDuplicate_whenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("taken@example.com", "supersecret1", "Someone");
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void refresh_rejectsExpiredToken() {
        RefreshToken expired = new RefreshToken(1L, "hashed-token", Instant.now().minusSeconds(60));
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_rejectsUnknownToken() {
        when(jwtService.hashToken("bogus")).thenReturn("hashed-bogus");
        when(refreshTokenRepository.findByTokenHash("hashed-bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("bogus"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_revokesOldToken_andIssuesNewPair_whenValid() {
        RefreshToken valid = new RefreshToken(7L, "hashed-token", Instant.now().plusSeconds(3600));
        setId(valid, 99L);
        User user = new User("user@example.com", "hash", "User Name");
        setId(user, 7L);

        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(valid));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(7L, "user@example.com")).thenReturn("new-access-token");
        when(jwtService.generateRefreshTokenValue()).thenReturn("new-raw-refresh-token");
        when(jwtService.hashToken("new-raw-refresh-token")).thenReturn("new-hashed-refresh-token");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        AuthResponse response = authService.refresh("raw-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(valid.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(valid);
        verify(refreshTokenRepository).save(argThat(rt -> "new-hashed-refresh-token".equals(rt.getTokenHash())));
    }

    /** Test-only helper: entities generate ids via the DB, so tests set them via reflection. */
    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
