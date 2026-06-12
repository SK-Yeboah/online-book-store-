package com.bookstore.service;

import com.bookstore.entity.RefreshToken;
import com.bookstore.entity.User;
import com.bookstore.exception.InvalidTokenException;
import com.bookstore.exception.TokenExpiredException;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.RefreshTokenRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.support.TestDatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RefreshTokenService — integration")
class RefreshTokenServiceTest {

    @Autowired RefreshTokenService refreshTokenService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired CartRepository cartRepository;
    @Autowired TestDatabaseCleaner dbCleaner;

    private User testUser;

    @BeforeEach
    void setUp() {
        dbCleaner.resetUserRelatedTables();
        testUser = userRepository.save(
                new User("rftestuser", passwordEncoder.encode("pass"), "rf@test.com", User.Role.ROLE_USER));
    }

    // ── createRefreshToken ─────────────────────────────────────────────────────

    @Test
    @DisplayName("createRefreshToken — persists a non-null, non-expired token for the user")
    void createRefreshToken_persistsValidToken() {
        RefreshToken token = refreshTokenService.createRefreshToken("rftestuser");

        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getUser().getUsername()).isEqualTo("rftestuser");
        assertThat(token.isExpired()).isFalse();
        assertThat(token.isUsed()).isFalse();
        assertThat(refreshTokenRepository.findByToken(token.getToken())).isPresent();
    }

    @Test
    @DisplayName("createRefreshToken — each call produces a unique token string")
    void createRefreshToken_uniquePerCall() {
        String t1 = refreshTokenService.createRefreshToken("rftestuser").getToken();
        String t2 = refreshTokenService.createRefreshToken("rftestuser").getToken();
        assertThat(t1).isNotEqualTo(t2);
    }

    // ── validateRefreshToken ───────────────────────────────────────────────────

    @Test
    @DisplayName("validateRefreshToken — returns token when valid")
    void validateRefreshToken_returnsToken_whenValid() {
        RefreshToken saved = refreshTokenService.createRefreshToken("rftestuser");

        RefreshToken result = refreshTokenService.validateRefreshToken(saved.getToken());

        assertThat(result.getToken()).isEqualTo(saved.getToken());
    }

    @Test
    @DisplayName("validateRefreshToken — throws InvalidTokenException for unknown token")
    void validateRefreshToken_throwsInvalidToken_whenNotFound() {
        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("nonexistent-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("validateRefreshToken — throws TokenExpiredException and removes token when expired")
    void validateRefreshToken_throwsExpired_andDeletesToken_whenExpired() {
        // Insert an already-expired token directly via the repository
        refreshTokenRepository.save(
                new RefreshToken("expired-uuid", testUser, Instant.now().minusSeconds(60)));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("expired-uuid"))
                .isInstanceOf(TokenExpiredException.class);

        assertThat(refreshTokenRepository.findByToken("expired-uuid")).isEmpty();
    }

    @Test
    @DisplayName("validateRefreshToken — detects reuse, revokes ALL user tokens, throws InvalidTokenException")
    void validateRefreshToken_detectsTokenTheft_revokesAllSessions() {
        // Create two valid tokens for the same user
        RefreshToken token1 = refreshTokenService.createRefreshToken("rftestuser");
        refreshTokenService.createRefreshToken("rftestuser"); // token2 — should also be revoked

        // Rotate token1 — marks it used=true, creates token3
        refreshTokenService.rotateRefreshToken(token1);

        // Replay the old (used) token — should trigger theft detection
        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken(token1.getToken()))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("reuse detected");

        // ALL tokens for this user must have been deleted
        assertThat(refreshTokenRepository.findAll())
                .noneMatch(rt -> rt.getUser().getId().equals(testUser.getId()));
    }

    // ── rotateRefreshToken ─────────────────────────────────────────────────────

    @Test
    @DisplayName("rotateRefreshToken — old token is marked used (not deleted), new token is created")
    void rotateRefreshToken_marksOldUsed_createsNewToken() {
        RefreshToken original = refreshTokenService.createRefreshToken("rftestuser");
        String originalTokenStr = original.getToken();

        RefreshToken rotated = refreshTokenService.rotateRefreshToken(original);

        // New token is different and valid
        assertThat(rotated.getToken()).isNotEqualTo(originalTokenStr);
        assertThat(rotated.isUsed()).isFalse();
        assertThat(rotated.isExpired()).isFalse();

        // Old token is still in DB but marked used
        RefreshToken oldInDb = refreshTokenRepository.findByToken(originalTokenStr).orElseThrow();
        assertThat(oldInDb.isUsed()).isTrue();
    }

    // ── revokeToken ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeToken — removes the single specified token")
    void revokeToken_deletesOnlyTargetToken() {
        RefreshToken t1 = refreshTokenService.createRefreshToken("rftestuser");
        RefreshToken t2 = refreshTokenService.createRefreshToken("rftestuser");

        refreshTokenService.revokeToken(t1.getToken());

        assertThat(refreshTokenRepository.findByToken(t1.getToken())).isEmpty();
        assertThat(refreshTokenRepository.findByToken(t2.getToken())).isPresent();
    }

    // ── revokeAllUserTokens ────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeAllUserTokens — removes every token belonging to the user")
    void revokeAllUserTokens_deletesAll() {
        refreshTokenService.createRefreshToken("rftestuser");
        refreshTokenService.createRefreshToken("rftestuser");

        refreshTokenService.revokeAllUserTokens(testUser.getId());

        assertThat(refreshTokenRepository.findAll())
                .noneMatch(rt -> rt.getUser().getId().equals(testUser.getId()));
    }

    // ── cleanupExpiredTokens ───────────────────────────────────────────────────

    @Test
    @DisplayName("cleanupExpiredTokens — removes expired tokens, keeps valid ones")
    void cleanupExpiredTokens_purguesExpiredOnly() {
        RefreshToken valid = refreshTokenService.createRefreshToken("rftestuser");
        refreshTokenRepository.save(
                new RefreshToken("stale-token", testUser, Instant.now().minusSeconds(1)));

        refreshTokenService.cleanupExpiredTokens();

        assertThat(refreshTokenRepository.findByToken("stale-token")).isNotPresent();
        assertThat(refreshTokenRepository.findByToken(valid.getToken())).isPresent();
    }
}
