package com.bookstore.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bookstore.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByIdAndUser_Id(Long id, Long userId);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Order> findByStatusAndCreatedAtBefore(Order.OrderStatus status, LocalDateTime cutoff, Pageable pageable);
}
