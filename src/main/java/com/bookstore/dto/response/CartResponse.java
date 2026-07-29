package com.bookstore.dto.response;

import java.util.List;

import com.bookstore.entity.CartItem;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private Long cartId;
    private List<CartItemResponse> items;
    private Integer totalItems;
    private Double totalPrice;

    public static CartResponse from (Long cartId, List<CartItem> items){
        
        List<CartItemResponse> itemResponses = items.stream()
            .map(CartItemResponse::from)
            .toList();

        int totalItems = items.stream()
            .mapToInt(CartItem::getQuantity)
            .sum();

        double totalPrice = items.stream()
            .mapToDouble(item -> item.getBook().getPrice() * item.getQuantity())
            .sum();

        return CartResponse.builder()
                .cartId(cartId)
                .items(itemResponses)
                .totalItems(totalItems)
                .totalPrice(totalPrice)
                .build();

    }
    
    
}
