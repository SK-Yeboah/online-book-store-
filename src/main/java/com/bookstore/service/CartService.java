package com.bookstore.service;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.CartResponse;

public interface CartService {

    /** Returns the user's cart, creating an empty one if it doesn't exist yet. */
    CartResponse getCart(Long userId);

    /** Adds a book or merges quantity if the is already in the cart. */
    CartResponse addItem(Long userId, AddToCartRequest request);
    
    /* updates quantity for a book already in the cart */
    CartResponse updateItemQuantity(Long userId, Long bookId, UpdateCartItemRequest request);

    /*Removes one book line from the cart */
    CartResponse removeItem(Long userId, Long bookId);

    /* Removes all items from the cart (keeps the cart row) */
    CartResponse clearCart(Long userId);
    
}
