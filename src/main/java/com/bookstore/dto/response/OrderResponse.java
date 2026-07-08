package com.bookstore.dto.response;

import com.bookstore.entity.Order;
import com.bookstore.entity.OrderItem;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long                    orderId;
    private List<OrderItemResponse> items;
    private Double                  totalAmount;
    private Order.OrderStatus       status;
    private LocalDateTime           createdAt;


    public static OrderResponse from(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                itemResponses,
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt());
    }
}