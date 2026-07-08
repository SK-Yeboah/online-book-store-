package com.bookstore.service;

import java.util.List;

import com.bookstore.dto.response.OrderResponse;

public interface OrderService {
    OrderResponse checkout(Long userId);
    List<OrderResponse> findByUser(Long userId);
    OrderResponse findById(Long userId, Long orderId); 
    
}
