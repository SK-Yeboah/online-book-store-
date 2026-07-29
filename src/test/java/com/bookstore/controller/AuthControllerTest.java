package com.bookstore.controller;

import com.bookstore.dto.request.LoginRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Objects;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthController — integration")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register — 201 on valid request")
    void register_validRequest_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "test@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
        .andExpect(status().isCreated())
        .andExpect(content().string("User registered successfully"));
    }

    @Test
    @DisplayName("POST /api/auth/register — 400 when username is blank")
    void register_blankUsername_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest("", "test@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='username')]").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register — 400 when email is invalid")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "not-an-email", "password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register — 409 on duplicate username")
    void register_duplicateUsername_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest("dupuser", "first@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
                .andExpect(status().isCreated());

        RegisterRequest dup = new RegisterRequest("dupuser", "second@example.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(dup))))
                .andExpect(status().isConflict());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login — 200 with token pair on valid credentials")
    void login_validCredentials_returns200WithTokens() throws Exception {
        // Register first
        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                        new RegisterRequest("loginuser", "login@example.com", "password123")))))
                .andExpect(status().isCreated());

        // Then login
        LoginRequest login = new LoginRequest("loginuser", "password123");
        mockMvc.perform(post("/api/auth/login")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(login))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("loginuser"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/login — 401 on wrong password")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                        new RegisterRequest("wrongpwuser", "wrongpw@example.com", "password123")))))
                .andExpect(status().isCreated());

        LoginRequest bad = new LoginRequest("wrongpwuser", "wrongpassword");
        mockMvc.perform(post("/api/auth/login")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(bad))))
                .andExpect(status().isUnauthorized());
    }
}
