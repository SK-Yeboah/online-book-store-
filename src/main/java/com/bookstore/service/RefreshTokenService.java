package com.bookstore.service;

import com.bookstore.entity.RefreshToken;
import com.bookstore.entity.User;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(User user);

    RefreshToken validateRefreshToken(String rawToken);

    RefreshToken rotateRefreshToken(RefreshToken current);

    void revokeToken(String rawToken);

    void revokeAllUserTokens(Long userId);

    void cleanupExpiredTokens();
}
