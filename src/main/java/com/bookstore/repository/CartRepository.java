package com.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bookstore.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {
    
}