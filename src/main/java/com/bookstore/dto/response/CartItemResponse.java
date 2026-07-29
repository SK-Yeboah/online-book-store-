package com.bookstore.dto.response;

import com.bookstore.entity.CartItem;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private Long cartItemId;
    private Long bookId;
    private String bookTitle;
    private Double bookPrice;
    private Integer quantity;
    private Double subtotal;

    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(
                item.getId(),
                item.getBook().getId(),
                item.getBook().getTitle(),
                item.getBook().getPrice(),
                item.getQuantity(),
                item.getBook().getPrice() * item.getQuantity());
    }
}