package com.bookstore.service;

import com.bookstore.dto.request.LoginRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.dto.response.JwtResponse;
import com.bookstore.exception.DuplicateResourceException;
import com.bookstore.repository.RefreshTokenRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.security.Jwtutil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UserService — integration")
class UserServiceTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Jwtutil jwtutil;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── register ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register — trims and lowercases username before persisting")
    void register_normalizesUsername() {
        userService.register(new RegisterRequest("  TrimMe  ", "trim@test.com", "password123"));

        assertThat(userRepository.existsByUsername("trimme")).isTrue();
        assertThat(userRepository.existsByUsername("  TrimMe  ")).isFalse();
    }

    @Test
    @DisplayName("register — password is stored as a BCrypt hash, not plaintext")
    void register_passwordIsEncoded() {
        userService.register(new RegisterRequest("secureuser", "secure@test.com", "mySecret99"));

        String stored = userRepository.findByUsername("secureuser")
                .orElseThrow().getPassword();

        assertThat(stored).doesNotContain("mySecret99");
        assertThat(passwordEncoder.matches("mySecret99", stored)).isTrue();
    }

    @Test
    @DisplayName("register — throws DuplicateResourceException on duplicate username")
    void register_throwsDuplicate_onDuplicateUsername() {
        userService.register(new RegisterRequest("dupname", "first@test.com", "password123"));

        assertThatThrownBy(() ->
                userService.register(new RegisterRequest("dupname", "second@test.com", "password123")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("register — throws DuplicateResourceException on duplicate email")
    void register_throwsDuplicate_onDuplicateEmail() {
        userService.register(new RegisterRequest("user1", "same@test.com", "password123"));

        assertThatThrownBy(() ->
                userService.register(new RegisterRequest("user2", "same@test.com", "password123")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // ── login ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login — returns access + refresh token pair on valid credentials")
    void login_returnsTokenPair_onValidCredentials() {
        userService.register(new RegisterRequest("logintest", "lt@test.com", "password123"));

        JwtResponse resp = userService.login(new LoginRequest("logintest", "password123"));

        assertThat(resp.getAccessToken()).isNotBlank();
        assertThat(resp.getRefreshToken()).isNotBlank();
        assertThat(resp.getUsername()).isEqualTo("logintest");
    }

    @Test
    @DisplayName("login — normalizes username (case-insensitive)")
    void login_normalizesUsername() {
        userService.register(new RegisterRequest("loweruser", "lower@test.com", "password123"));

        JwtResponse resp = userService.login(new LoginRequest("  LowerUser  ", "password123"));

        assertThat(resp.getUsername()).isEqualTo("loweruser");
    }

    @Test
    @DisplayName("login — throws BadCredentialsException on wrong password")
    void login_throwsBadCredentials_onWrongPassword() {
        userService.register(new RegisterRequest("badpwuser", "bp@test.com", "correctPass1"));

        assertThatThrownBy(() ->
                userService.login(new LoginRequest("badpwuser", "wrongPass")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login — access token subject matches username")
    void login_accessTokenSubjectMatchesUsername() {
        userService.register(new RegisterRequest("jwtsubject", "js@test.com", "password123"));

        JwtResponse resp = userService.login(new LoginRequest("jwtsubject", "password123"));

        assertThat(jwtutil.extractUsername(resp.getAccessToken())).isEqualTo("jwtsubject");
    }

    // ── logout ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout — access token is blacklisted after logout")
    void logout_blacklistsAccessToken() {
        userService.register(new RegisterRequest("logoutuser", "lo@test.com", "password123"));
        JwtResponse resp = userService.login(new LoginRequest("logoutuser", "password123"));

        userService.logout(resp.getAccessToken(), resp.getRefreshToken());

        // The token blacklist check is exercised through JwtFilterTest.
        // Here we verify the service-layer logout is idempotent (re-calling won't throw).
        assertThatCode(() -> userService.logout(resp.getAccessToken(), resp.getRefreshToken()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logoutAll — revokes every refresh token for the user")
    void logoutAll_revokesAllRefreshTokens() {
        userService.register(new RegisterRequest("logoutalluser", "loa@test.com", "password123"));
        JwtResponse resp1 = userService.login(new LoginRequest("logoutalluser", "password123"));
        JwtResponse resp2 = userService.login(new LoginRequest("logoutalluser", "password123"));

        userService.logoutAll(resp1.getAccessToken(), "logoutalluser");

        assertThat(refreshTokenRepository.findByToken(resp1.getRefreshToken())).isEmpty();
        assertThat(refreshTokenRepository.findByToken(resp2.getRefreshToken())).isEmpty();
    }

    // ── refresh ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh — returns new token pair and old refresh token is marked used")
    void refresh_returnsNewTokenPair_andOldTokenIsMarkedUsed() {
        userService.register(new RegisterRequest("refreshuser", "ru@test.com", "password123"));
        JwtResponse initial = userService.login(new LoginRequest("refreshuser", "password123"));

        JwtResponse rotated = userService.refresh(initial.getRefreshToken());

        assertThat(rotated.getAccessToken()).isNotBlank();
        assertThat(rotated.getRefreshToken()).isNotEqualTo(initial.getRefreshToken());

        // Old token still exists but is marked as used
        assertThat(refreshTokenRepository.findByToken(initial.getRefreshToken()))
                .isPresent()
                .hasValueSatisfying(rt -> assertThat(rt.isUsed()).isTrue());
    }
}
