package com.bookstore.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtUtil")
class JwtutilTest {

    private Jwtutil jwtUtil;

    private static final String TEST_SECRET_HEX =
            "74657374736563726574746573747365637265747465737473656372657474657374";

    @BeforeEach
    void setUp() {
        jwtUtil = new Jwtutil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET_HEX);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 900_000L);
        jwtUtil.init();
    }

    @Test
    @DisplayName("generates a token containing the correct username")
    void generateToken_containsUsername() {
        UserDetails user = buildUser("alice");
        String token = jwtUtil.generateAccessToken(user);
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("generates a token with a unique JTI on each call")
    void generateToken_uniqueJti() {
        UserDetails user = buildUser("alice");
        String token1 = jwtUtil.generateAccessToken(user);
        String token2 = jwtUtil.generateAccessToken(user);
        assertThat(jwtUtil.extractJti(token1)).isNotEqualTo(jwtUtil.extractJti(token2));
    }

    @Test
    @DisplayName("validates a freshly generated token")
    void validateToken_freshToken_returnsTrue() {
        UserDetails user = buildUser("alice");
        String token = jwtUtil.generateAccessToken(user);
        assertThat(jwtUtil.validateToken(token, user)).isTrue();
    }

    @Test
    @DisplayName("rejects a token for a different user")
    void validateToken_wrongUser_returnsFalse() {
        UserDetails alice = buildUser("alice");
        UserDetails bob   = buildUser("bob");
        String token = jwtUtil.generateAccessToken(alice);
        assertThat(jwtUtil.validateToken(token, bob)).isFalse();
    }

    @Test
    @DisplayName("rejects a blank token")
    void validateToken_blankToken_returnsFalse() {
        UserDetails user = buildUser("alice");
        assertThat(jwtUtil.validateToken("", user)).isFalse();
        assertThat(jwtUtil.validateToken("  ", user)).isFalse();
    }

    @Test
    @DisplayName("rejects a tampered token")
    void validateToken_tamperedToken_returnsFalse() {
        UserDetails user = buildUser("alice");
        String token = jwtUtil.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(jwtUtil.validateToken(tampered, user)).isFalse();
    }

    @Test
    @DisplayName("fails to initialise when secret is blank")
    void init_blankSecret_throwsIllegalState() {
        Jwtutil bad = new Jwtutil();
        ReflectionTestUtils.setField(bad, "secret", "");
        ReflectionTestUtils.setField(bad, "accessTokenExpiration", 900_000L);
        assertThatThrownBy(bad::init).isInstanceOf(IllegalStateException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserDetails buildUser(String username) {
        return new User(username, "irrelevant",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
