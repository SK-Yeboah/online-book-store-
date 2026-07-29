package com.bookstore.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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
import com.bookstore.dto.request.CreatePaymentIntentRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AdminPaymentController — integration")
class AdminPaymentControllerTest {

    private static final String USERNAME = "adminpayuser";
    private static final String IDEMPOTENCY_KEY = "admin_pay_1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookRepository bookRepository;
    @Autowired PaymentRepository paymentRepository;

    private Long bookId;
    private Long orderId;
    private Long paymentId;
    private int initialStock;

    @BeforeEach
    void setUp() throws Exception {
        registerUser();
        Book book = bookRepository.save(new Book(
                "Admin Pay Book", "Author", "Fiction", "978-0000000299",
                25.00, 8, "Description"));
        bookId = book.getId();
        initialStock = book.getStockQuantity();
        orderId = checkout();
        paymentId = createIntentAndConfirm();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("GET /api/admin/payments — 403 for non-admin")
    void list_user_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/payments — lists payments")
    void list_admin_returnsPayments() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .param("status", "SUCCEEDED")
                        .param("provider", "mock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$.content[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.content[0].provider").value("mock"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/payments/{id} — returns payment")
    void getById_returnsPayment() throws Exception {
        mockMvc.perform(get("/api/admin/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.orderId").value(orderId));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/payments/{id}/refund — refunds and cancels order")
    void refund_succeeds() throws Exception {
        mockMvc.perform(post("/api/admin/payments/{id}/refund", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(get("/api/admin/orders")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USERNAME).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Book book = bookRepository.findById(bookId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(book.getStockQuantity()).isEqualTo(initialStock);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/payments/{id}/refund — idempotent when already refunded")
    void refund_idempotent() throws Exception {
        mockMvc.perform(post("/api/admin/payments/{id}/refund", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(post("/api/admin/payments/{id}/refund", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/payments/{id}/refund — 400 when not succeeded")
    void refund_requiresAction_rejected() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USERNAME).roles("USER"))
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new AddToCartRequest(bookId, 1)))))
                .andExpect(status().isCreated());

        String json = mockMvc.perform(post("/api/orders/checkout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USERNAME).roles("USER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long pendingOrderId = objectMapper.readTree(json).get("orderId").asLong();

        mockMvc.perform(post("/api/payments/intent")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USERNAME).roles("USER"))
                        .header("Idempotency-Key", "admin_pay_pending")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new CreatePaymentIntentRequest(pendingOrderId, null, null)))))
                .andExpect(status().isOk());

        Long pendingPaymentId = paymentRepository.findByIdempotencyKey("admin_pay_pending")
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/admin/payments/{id}/refund", pendingPaymentId))
                .andExpect(status().isBadRequest());
    }

    private Long createIntentAndConfirm() throws Exception {
        createIntentOnly(IDEMPOTENCY_KEY);

        String webhookBody = objectMapper.writeValueAsString(Map.of(
                "reference", IDEMPOTENCY_KEY,
                "success", true,
                "providerPaymentId", "mock_admin_1",
                "eventId", "mock.charge.success"));

        mockMvc.perform(post("/api/payments/webhook/{provider}", "mock")
                        .header("X-Webhook-Secret", "test_webhook_secret")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(webhookBody))
                .andExpect(status().isOk());

        return paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY).orElseThrow().getId();
    }

    private Long createIntentOnly(String key) throws Exception {
        mockMvc.perform(post("/api/payments/intent")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USERNAME).roles("USER"))
                        .header("Idempotency-Key", key)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new CreatePaymentIntentRequest(orderId, null, null)))))
                .andExpect(status().isOk());
        return paymentRepository.findByIdempotencyKey(key).orElseThrow().getId();
    }

    private void registerUser() throws Exception {
        RegisterRequest req = new RegisterRequest(USERNAME, "adminpay@example.com", "password1234");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
                .andExpect(status().isCreated());
    }

    private Long checkout() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USERNAME).roles("USER"))
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new AddToCartRequest(bookId, 1)))))
                .andExpect(status().isCreated());

        String json = mockMvc.perform(post("/api/orders/checkout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USERNAME).roles("USER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json).get("orderId").asLong();
    }
}
