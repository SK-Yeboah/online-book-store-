package com.bookstore.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bookstore.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            SELECT oi FROM OrderItem oi
            JOIN FETCH oi.book
            WHERE oi.order.id = :orderId
            """)
    List<OrderItem> findByOrderIdWithBooks(@Param("orderId") Long orderId);
}
