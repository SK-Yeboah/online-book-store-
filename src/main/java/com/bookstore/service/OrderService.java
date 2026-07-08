package com.bookstore.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.dto.response.OrderResponse;
import com.bookstore.dto.response.PagedResponse;

public interface OrderService {
    OrderResponse checkout(Long userId);
    List<OrderResponse> findByUser(Long userId);
    OrderResponse findById(Long userId, Long orderId);
    PagedResponse<OrderResponse> findAll(Pageable pageable);
    OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request);
}
