package com.bookstore.service;

import com.bookstore.entity.RefreshToken;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(String username);

    RefreshToken validateRefreshToken(String rawToken);

    RefreshToken rotateRefreshToken(RefreshToken current);

    void revokeToken(String rawToken);

    void revokeAllUserTokens(Long userId);

    void cleanupExpiredTokens();
}
