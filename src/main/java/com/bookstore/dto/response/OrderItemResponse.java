package com.bookstore.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private Long    bookId;
    private String  bookTitle;
    private Integer quantity;
    private Double  priceAtPurchase;
    private Double  subtotal;
}