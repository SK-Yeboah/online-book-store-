package com.bookstore.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.entity.Book;
import com.bookstore.entity.Order.OrderStatus;
import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AdminOrderController — integration")
class AdminOrderControllerTest {

    private static final String USERNAME = "adminorderuser";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookRepository bookRepository;

    private Long bookId;
    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        registerUser();
        bookId = bookRepository.save(new Book(
                "Admin Order Book", "Author", "Fiction", "978-0000000003",
                19.99, 10, "Description")).getId();
        orderId = placeOrder();
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{id}/status — 403 for non-admin")
    @WithMockUser(username = USERNAME, roles = "USER")
    void updateStatus_user_returns403() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest(OrderStatus.SHIPPED)))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{id}/status — CONFIRMED to SHIPPED")
    @WithMockUser(roles = "ADMIN")
    void updateStatus_confirmedToShipped_returns200() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest(OrderStatus.SHIPPED)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{id}/status — SHIPPED to DELIVERED")
    @WithMockUser(roles = "ADMIN")
    void updateStatus_shippedToDelivered_returns200() throws Exception {
        updateStatusAsAdmin(orderId, OrderStatus.SHIPPED);

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest(OrderStatus.DELIVERED)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{id}/status — 400 invalid skip to DELIVERED")
    @WithMockUser(roles = "ADMIN")
    void updateStatus_confirmedToDelivered_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest(OrderStatus.DELIVERED)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{id}/status — CANCELLED restores stock")
    @WithMockUser(roles = "ADMIN")
    void updateStatus_cancelled_restoresStock() throws Exception {
        Book before = bookRepository.findById(bookId).orElseThrow();
        int stockAfterCheckout = before.getStockQuantity();

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest(OrderStatus.CANCELLED)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Book after = bookRepository.findById(bookId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(after.getStockQuantity()).isEqualTo(stockAfterCheckout + 2);
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{id}/status — 400 when already cancelled")
    @WithMockUser(roles = "ADMIN")
    void updateStatus_alreadyCancelled_returns400() throws Exception {
        updateStatusAsAdmin(orderId, OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest(OrderStatus.SHIPPED)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ORDER_ALREADY_CANCELLED"));
    }

    @Test
    @DisplayName("GET /api/admin/orders — 200 for admin")
    @WithMockUser(roles = "ADMIN")
    void listOrders_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").isNumber())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private void registerUser() throws Exception {
        RegisterRequest req = new RegisterRequest(USERNAME, "adminorder@example.com", "password1234");
        mockMvc.perform(post("/api/auth/register")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
                .andExpect(status().isCreated());
    }

    private Long placeOrder() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(USERNAME).roles("USER"))
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(new AddToCartRequest(bookId, 2)))))
                .andExpect(status().isCreated());

        String json = mockMvc.perform(post("/api/orders/checkout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(USERNAME).roles("USER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json).get("orderId").asLong();
    }

    private void updateStatusAsAdmin(Long id, OrderStatus status) throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{id}/status", id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("admin").roles("ADMIN"))
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest(status)))))
                .andExpect(status().isOk());
    }
}
