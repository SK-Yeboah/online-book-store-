package com.bookstore.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookstore.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUser_Id(Long userId);
}