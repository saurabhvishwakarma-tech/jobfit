package com.jobfit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobfit.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration test: real Postgres (via Testcontainers), real
 * Flyway migrations, real Spring Security filter chain. Exercises the
 * complete auth lifecycle end to end - this is the kind of test that would
 * have caught wiring mistakes the unit tests can't see (e.g. a
 * misconfigured SecurityFilterChain letting an unauthenticated request
 * through).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Import(PostgresTestContainerConfig.class)
@ActiveProfiles("it")
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshAndAccessProtectedResource() throws Exception {
        String registerBody = objectMapper.writeValueAsString(new RegisterRequestBody(
                "new.user@example.com", "correct-horse-battery", "New User"));

        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("new.user@example.com"))
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();
        String refreshToken = objectMapper.readTree(registerResponse).get("refreshToken").asText();

        // Protected endpoint rejects unauthenticated requests.
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        // ... and accepts a valid bearer token.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.user@example.com"));

        // Duplicate registration is rejected with 409.
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody))
                .andExpect(status().isConflict());

        // Wrong password is rejected with 401, not 500 or a stack trace.
        String badLogin = objectMapper.writeValueAsString(
                new LoginRequestBody("new.user@example.com", "wrong-password"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(badLogin))
                .andExpect(status().isUnauthorized());

        // Refresh rotates the token and issues a new working access token.
        String refreshBody = objectMapper.writeValueAsString(new RefreshRequestBody(refreshToken));
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // The rotated (used) refresh token can no longer be reused.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    private record RegisterRequestBody(String email, String password, String fullName) {
    }

    private record LoginRequestBody(String email, String password) {
    }

    private record RefreshRequestBody(String refreshToken) {
    }
}
