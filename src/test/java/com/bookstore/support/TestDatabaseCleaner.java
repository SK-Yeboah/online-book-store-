package com.bookstore.support;

import org.springframework.stereotype.Component;

import com.bookstore.repository.*;

@Component
public class TestDatabaseCleaner {

    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public TestDatabaseCleaner(
        CartItemRepository cartItemRepository,
        CartRepository cartRepository,
        RefreshTokenRepository refreshTokenRepository,
        UserRepository userRepository) {
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public void resetUserRelatedTables() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }
    
}
