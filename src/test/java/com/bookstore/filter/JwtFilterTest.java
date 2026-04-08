package com.bookstore.filter;

import com.bookstore.dto.request.LoginRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.dto.response.JwtResponse;
import com.bookstore.repository.RefreshTokenRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("JwtFilter — token validation and blacklist enforcement")
class JwtFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userService.register(new RegisterRequest("jwtfilteruser", "jf@test.com", "password123"));
    }

    private JwtResponse doLogin() {
        return userService.login(new LoginRequest("jwtfilteruser", "password123"));
    }

    // ── No Authorization header → passes through (public routes work) ─────────

    @Test
    @DisplayName("request without Authorization header passes through filter chain")
    void noAuthHeader_passesThroughToChain() throws Exception {
        // /api/auth/register is public — reaches the controller without a JWT
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("noheaderuser", "nh@test.com", "password123"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ── Blacklisted token → 401 immediately ───────────────────────────────────

    @Test
    @DisplayName("blacklisted access token → 401 with 'Token has been revoked'")
    void blacklistedToken_returns401WithRevokedMessage() throws Exception {
        JwtResponse resp = doLogin();

        // Logout blacklists the access token
        userService.logout(resp.getAccessToken(), resp.getRefreshToken());

        // Any protected endpoint must now reject the old token
        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + resp.getAccessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token has been revoked"));
    }

    // ── Malformed / expired token → filter skips, Spring Security rejects ─────

    @Test
    @DisplayName("malformed token in Authorization header → 401 (filter skips, security rejects)")
    void malformedToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ── Valid token → request proceeds ────────────────────────────────────────

    @Test
    @DisplayName("valid JWT → authenticated request proceeds to controller")
    void validToken_requestProceeds() throws Exception {
        JwtResponse resp = doLogin();

        // /api/auth/refresh is public-ish (needs a refresh token param, not JWT)
        // but /api/auth/logout-all requires a valid JWT + ROLE_USER
        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + resp.getAccessToken()))
                .andExpect(status().isNoContent()); // 204 — logout succeeded
    }
}
