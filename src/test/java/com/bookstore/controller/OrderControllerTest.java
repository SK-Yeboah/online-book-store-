package com.bookstore.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("OrderController — integration")
class OrderControllerTest {

    private static final String USERNAME = "orderuser";
    private static final String OTHER_USER = "otherorderuser";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookRepository bookRepository;

    private Long bookId;

    @BeforeEach
    void setUp() throws Exception {
        registerUser(USERNAME, "order@example.com");
        Book book = bookRepository.save(new Book(
                "Order Test Book", "Author", "Fiction", "978-0000000002",
                19.99, 10, "Description"));
        bookId = book.getId();
    }

    @Test
    @DisplayName("POST /api/orders/checkout — 401 without auth")
    void checkout_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/orders/checkout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST /api/orders/checkout — 400 empty cart")
    void checkout_emptyCart_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/checkout"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EMPTY_CART"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST /api/orders/checkout — 400 insufficient stock")
    void checkout_insufficientStock_returns400() throws Exception {
        addToCart(bookId, 5);
        Book book = bookRepository.findById(bookId).orElseThrow();
        book.setStockQuantity(2);
        bookRepository.save(book);

        mockMvc.perform(post("/api/orders/checkout"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST /api/orders/checkout — 201 happy path")
    void checkout_happyPath_returns201() throws Exception {
        addToCart(bookId, 2);

        mockMvc.perform(post("/api/orders/checkout"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(39.98))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].priceAtPurchase").value(19.99));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        Book updated = bookRepository.findById(bookId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStockQuantity()).isEqualTo(8);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("GET /api/orders/{id} — 200 for own order")
    void getById_ownOrder_returns200() throws Exception {
        addToCart(bookId, 1);
        String json = mockMvc.perform(post("/api/orders/checkout"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(json).get("orderId").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId));
    }

    @Test
    @WithMockUser(username = OTHER_USER, roles = "USER")
    @DisplayName("GET /api/orders/{id} — 404 for another user's order")
    void getById_otherUsersOrder_returns404() throws Exception {
        addToCartAsUser(USERNAME, bookId, 1);
        String json = checkoutAsUser(USERNAME);
        Long orderId = objectMapper.readTree(json).get("orderId").asLong();

        registerUser(OTHER_USER, "other@example.com");

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("GET /api/orders — returns order history")
    void listOrders_returnsOwnOrders() throws Exception {
        addToCart(bookId, 1);
        mockMvc.perform(post("/api/orders/checkout"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").isNumber())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    private void registerUser(String username, String email) throws Exception {
        RegisterRequest req = new RegisterRequest(username, email, "password1234");
        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
                .andExpect(status().isCreated());
    }

    private void addToCart(Long bookId, int qty) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(new AddToCartRequest(bookId, qty)))))
                .andExpect(status().isCreated());
    }

    private void addToCartAsUser(String username, Long bookId, int qty) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(username).roles("USER"))
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(new AddToCartRequest(bookId, qty)))))
                .andExpect(status().isCreated());
    }

    private String checkoutAsUser(String username) throws Exception {
        return mockMvc.perform(post("/api/orders/checkout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(username).roles("USER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}
