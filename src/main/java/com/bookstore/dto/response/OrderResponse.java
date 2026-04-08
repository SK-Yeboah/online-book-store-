package com.bookstore.dto.response;

import com.bookstore.entity.Order;
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
}