package com.bookstore.support;

import org.springframework.stereotype.Component;

import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.OrderItemRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.PaymentRepository;
import com.bookstore.repository.RefreshTokenRepository;
import com.bookstore.repository.UserRepository;

@Component
public class TestDatabaseCleaner {

    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public TestDatabaseCleaner(
            PaymentRepository paymentRepository,
            OrderItemRepository orderItemRepository,
            OrderRepository orderRepository,
            CartItemRepository cartItemRepository,
            CartRepository cartRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public void resetUserRelatedTables() {
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
