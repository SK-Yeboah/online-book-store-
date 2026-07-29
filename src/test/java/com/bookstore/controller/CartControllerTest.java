package com.bookstore.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.BookRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("CartController - integration")
public class CartControllerTest {

    private static final String USERNAME = "cartuser";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookRepository bookRepository;

    private Long bookId;

    @BeforeEach
    void setUp() throws Exception{
        registerUser();
        Book book = bookRepository.save(new Book(
            "Test book", "Autho", "Fiction", "978-0000000001", 
            19.99, 10, "Description"
        ));
        bookId = book.getId();
    }

    @Test
    @DisplayName("GET /api/cart - 401 without auth")
    void geteCart_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST /api/cart - 200 empty cart")
    void getCart_empty_returns200() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems")
                .value(0));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST /api/cart/items - 201")
    void addItem_valid_return201() throws Exception {
        addToCart(bookId, 2);
        mockMvc.perform(get("/api/cart"))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST - 400 insufficient stock")
    void addItem_overStock_return400() throws Exception {
        mockMvc.perform(post("/api/cart/items")
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(Objects.requireNonNull(
                objectMapper.writeValueAsString(new AddToCartRequest(bookId, 999)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST — 404 unknown book")
    void addItem_unknownBook_returns404() throws Exception {
        mockMvc.perform(post("/api/cart/items")
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(Objects.requireNonNull(
                objectMapper.writeValueAsString(new AddToCartRequest(999999L, 1)))))
        .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("PATCH /api/cart/items/{bookId} — 200")
    void updateQuantity_valid_returns200() throws Exception {
        addToCart(bookId, 2);
        mockMvc.perform(patch("/api/cart/items/{bookId}", bookId)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(new UpdateCartItemRequest(5)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("DELETE /api/cart/items/{bookId} — 200")
    void removeItem_valid_returns200() throws Exception {
        addToCart(bookId, 1);
        mockMvc.perform(delete("/api/cart/items/{bookId}", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("DELETE /api/cart — 200 clears cart")
    void clearCart_valid_returns200() throws Exception {
        addToCart(bookId, 3);
        mockMvc.perform(delete("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    // Helpers
    private void registerUser() throws Exception {
        RegisterRequest req = new RegisterRequest(USERNAME, "cart@example.com", "password1234");
        mockMvc.perform(post("/api/auth/register")
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON_VALUE))
        .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
        .andExpect(status().isCreated());
    }

    @WithMockUser(roles = "ADMIN")
    private Long createBookAsAdmin() throws Exception {
        BookRequest book = new BookRequest(
                "Test Book", "Author", "Fiction",
                "978-0000000001", 19.99, 10, "Description");
        String json = mockMvc.perform(post("/api/admin/books")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(book))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }
    
    private void addToCart(Long bookId, int qty) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(new AddToCartRequest(bookId, qty)))))
                .andExpect(status().isCreated());
    }
    
    
}
