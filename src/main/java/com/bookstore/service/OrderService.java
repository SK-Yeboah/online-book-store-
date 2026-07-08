package com.bookstore.service;

import org.springframework.data.domain.Pageable;

import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.dto.response.OrderResponse;
import com.bookstore.dto.response.PagedResponse;

public interface OrderService {
    OrderResponse checkout(Long userId);
    PagedResponse<OrderResponse> findByUser(Long userId, Pageable pageable);
    OrderResponse findById(Long userId, Long orderId);
    PagedResponse<OrderResponse> findAll(Pageable pageable);
    OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request);
}
