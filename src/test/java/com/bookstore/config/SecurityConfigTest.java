package com.bookstore.config;

import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.RefreshTokenRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.support.TestDatabaseCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityConfig — access-control rules")
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired CartRepository cartRepository;
    @Autowired TestDatabaseCleaner dbCleaner;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        dbCleaner.resetUserRelatedTables();
    }

    // ── Public endpoints — no credentials required ─────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register is public — no JWT needed")
    void register_isPublic() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("publictest", "pub@test.com", "password123"));

                mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(body)))
        .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /actuator/health is public — no JWT needed")
    void actuatorHealth_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/prometheus is not blocked by auth when prometheus-public=true")
    void actuatorPrometheus_notUnauthorizedWhenPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    // ── Protected endpoints — unauthenticated → 401 ───────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout without JWT → 401")
    void logout_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .param("refreshToken", "some-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout-all without JWT → 401")
    void logoutAll_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isUnauthorized());
    }

    // ── Role-based access — USER vs ADMIN ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/admin/** with ROLE_USER → 403 Forbidden")
    @WithMockUser(roles = "USER")
    void adminEndpoint_withUserRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/** with ROLE_ADMIN → not 403 (endpoint may not exist → 404 is fine)")
    @WithMockUser(roles = "ADMIN")
    void adminEndpoint_withAdminRole_isNotForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    // ── GET /api/books/** is public ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books is public — unauthenticated → not 401")
    void getBooks_isPublic() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }
}
