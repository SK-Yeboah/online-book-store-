package com.bookstore.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import com.bookstore.entity.OrderItem;

import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    
    private Long bookId;
    private String bookTitle;
    private Integer quantity;
    private Double priceAtPurchase;
    private Double subtotal;
    
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
            item.getBook().getId(),
            item.getBook().getTitle(),
            item.getQuantity(),
            item.getPriceAtPurchase(),
            item.getPriceAtPurchase() * item.getQuantity());
    }


}