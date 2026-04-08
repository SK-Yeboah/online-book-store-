package com.bookstore.exception;

import com.bookstore.dto.request.LoginRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.repository.RefreshTokenRepository;
import com.bookstore.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("GlobalSecurityExceptionHandler — response shapes")
class GlobalSecurityExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── 400 Validation (MethodArgumentNotValidException) ─────────────────────

    @Test
    @DisplayName("POST /api/auth/register with blank username → 400 with fieldErrors array")
    void validationFailure_returns400WithFieldErrors() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("", "bad-email", ""));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    // ── 401 Invalid Credentials (InvalidCredentialsException) ─────────────────

    @Test
    @DisplayName("POST /api/auth/login with wrong password → 401 with remainingAttempts")
    void invalidCredentials_returns401WithRemainingAttempts() throws Exception {
        // Register so the user exists
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("exhandleruser", "ex@test.com", "correct123"))))
                .andExpect(status().isCreated());

        String badLogin = objectMapper.writeValueAsString(
                new LoginRequest("exhandleruser", "wrongpassword"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid Credentials"))
                .andExpect(jsonPath("$.remainingAttempts").isNumber());
    }

    // ── 423 Account Locked (AccountLockedException) ───────────────────────────

    @Test
    @DisplayName("POST /api/auth/login after 5 failures → 423 with minutesRemaining")
    void accountLocked_returns423() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("lockcandidate", "lock@test.com", "correct123"))))
                .andExpect(status().isCreated());

        String badLogin = objectMapper.writeValueAsString(
                new LoginRequest("lockcandidate", "wrongpassword"));

        // Exhaust the 5 allowed attempts
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badLogin));
        }

        // 6th attempt — account should now be locked
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("Account Locked"))
                .andExpect(jsonPath("$.message").value(containsString("minutes")));
    }

    // ── 400 Missing Request Parameter ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh without ?refreshToken → 400 with parameter name")
    @WithMockUser
    void missingRequestParam_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("refreshToken")));
    }

    // ── 403 Access Denied (AccessDeniedException) ─────────────────────────────

    @Test
    @DisplayName("GET /api/admin/** with ROLE_USER → 403 with actual request path (not literal string)")
    @WithMockUser(roles = "USER")
    void accessDenied_returns403WithCorrectPath() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                // This assertion catches the Bug 1 regression: path must be the real URI
                .andExpect(jsonPath("$.path").value("/api/admin/users"))
                .andExpect(jsonPath("$.path").value(not("request.getRequestURI()")));
    }
}
