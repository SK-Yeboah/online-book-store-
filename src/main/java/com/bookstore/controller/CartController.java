package com.bookstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.CartResponse;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.CartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Authenticated shopping cart operation")
public class CartController {

    private final CartService cartService;
    private final UserRepository userRepository;

    @Operation(summary = "View cart")
    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(cartService.getCart(resolveUserId(userDetails)));
    }

    @Operation(summary = "Add item to cart")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddToCartRequest request) {
        CartResponse response = cartService.addItem(resolveUserId(userDetails), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update item quantity")
    @PatchMapping("/items/{bookId}")
    public ResponseEntity<CartResponse> updateQuantity(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bookId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(
                cartService.updateItemQuantity(resolveUserId(userDetails), bookId, request));
    }

    @Operation(summary = "Remove item from cart")
    @DeleteMapping("/items/{bookId}")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bookId) {
        return ResponseEntity.ok(cartService.removeItem(resolveUserId(userDetails), bookId));
    }
    @Operation(summary = "Clear cart")
    @DeleteMapping
    public ResponseEntity<CartResponse> clearCart(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(cartService.clearCart(resolveUserId(userDetails)));
    }
    

    //Helper
    private Long resolveUserId(UserDetails userDetails){
        return userRepository.findByUsername(userDetails.getUsername()).orElseThrow(() -> new ResourceNotFoundException("User", "Username", userDetails.getUsername())).getId();
    }
    
}
