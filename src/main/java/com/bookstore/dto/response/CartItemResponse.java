package com.bookstore.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private Long    cartItemId;
    private Long    bookId;
    private String  bookTitle;
    private Double  bookPrice;
    private Integer quantity;
    private Double  subtotal; // bookPrice × quantity
}