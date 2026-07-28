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
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("PaymentController — integration")
class PaymentControllerTest {

    private static final String USERNAME = "payuser";
    private static final String IDEMPOTENCY_KEY = "pay_test_order_1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookRepository bookRepository;

    private Long bookId;
    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        registerUser();
        bookId = bookRepository.save(new Book(
                "Payment Book", "Author", "Fiction", "978-0000000099",
                19.99, 10, "Description")).getId();
        orderId = checkout();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST /api/payments/intent — creates mock payment intent")
    void createIntent_returnsAuthorizationUrl() throws Exception {
        mockMvc.perform(post("/api/payments/intent")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new CreatePaymentIntentRequest(orderId, null, null)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.reference").value(IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.authorizationUrl").isNotEmpty())
                .andExpect(jsonPath("$.status").value("REQUIRES_ACTION"))
                .andExpect(jsonPath("$.provider").value("mock"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("POST /api/payments/webhook/mock — success confirms order")
    void webhook_success_marksOrderConfirmed() throws Exception {
        createIntent(IDEMPOTENCY_KEY);

        postWebhook(IDEMPOTENCY_KEY, true, "mock_evt_1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("duplicate webhook — second call is idempotent 200")
    void webhook_duplicate_isIdempotent() throws Exception {
        createIntent(IDEMPOTENCY_KEY);

        postWebhook(IDEMPOTENCY_KEY, true, "mock_evt_1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        postWebhook(IDEMPOTENCY_KEY, true, "mock_evt_1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("verify then webhook — race stays CONFIRMED")
    void verifyThenWebhook_isIdempotent() throws Exception {
        createIntent(IDEMPOTENCY_KEY);

        mockMvc.perform(get("/api/payments/verify/{reference}", IDEMPOTENCY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        postWebhook(IDEMPOTENCY_KEY, true, "mock_evt_race")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("second intent new key — reuses active payment")
    void createIntent_newKey_reusesActivePayment() throws Exception {
        createIntent(IDEMPOTENCY_KEY);

        mockMvc.perform(post("/api/payments/intent")
                        .header("Idempotency-Key", "pay_test_order_1_retry")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new CreatePaymentIntentRequest(orderId, null, null)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("REQUIRES_ACTION"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("same key after FAILED — requires new Idempotency-Key")
    void createIntent_sameKeyAfterFailure_rejected() throws Exception {
        createIntent(IDEMPOTENCY_KEY);

        postWebhook(IDEMPOTENCY_KEY, false, "mock_evt_fail")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(post("/api/payments/intent")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new CreatePaymentIntentRequest(orderId, null, null)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("new key after FAILED — creates fresh intent and reopens order")
    void createIntent_newKeyAfterFailure_createsNewPayment() throws Exception {
        createIntent(IDEMPOTENCY_KEY);

        postWebhook(IDEMPOTENCY_KEY, false, "mock_evt_fail")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

        String newKey = "pay_test_order_1_new";
        mockMvc.perform(post("/api/payments/intent")
                        .header("Idempotency-Key", newKey)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new CreatePaymentIntentRequest(orderId, null, null)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(newKey))
                .andExpect(jsonPath("$.status").value("REQUIRES_ACTION"));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("ignored webhook event — still HTTP 200")
    void webhook_ignoredEvent_returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("ignored", true));

        mockMvc.perform(post("/api/payments/webhook/{provider}", "mock")
                        .header("X-Webhook-Secret", "test_webhook_secret")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("GET /api/payments/verify/{reference} — confirms order when provider succeeds")
    void verify_marksOrderConfirmed() throws Exception {
        createIntent(IDEMPOTENCY_KEY);

        mockMvc.perform(get("/api/payments/verify/{reference}", IDEMPOTENCY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.reference").value(IDEMPOTENCY_KEY));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("GET /api/payments/order/{orderId} — returns payment")
    void getByOrder_returnsPayment() throws Exception {
        createIntent(IDEMPOTENCY_KEY);

        mockMvc.perform(get("/api/payments/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.reference").value(IDEMPOTENCY_KEY));
    }

    private void createIntent(String key) throws Exception {
        mockMvc.perform(post("/api/payments/intent")
                        .header("Idempotency-Key", key)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new CreatePaymentIntentRequest(orderId, null, null)))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions postWebhook(
            String reference, boolean success, String providerPaymentId) throws Exception {
        String webhookBody = objectMapper.writeValueAsString(Map.of(
                "reference", reference,
                "success", success,
                "providerPaymentId", providerPaymentId,
                "eventId", success ? "mock.charge.success" : "mock.charge.failed"));

        return mockMvc.perform(post("/api/payments/webhook/{provider}", "mock")
                .header("X-Webhook-Secret", "test_webhook_secret")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(webhookBody)));
    }

    private void registerUser() throws Exception {
        RegisterRequest req = new RegisterRequest(USERNAME, "pay@example.com", "password1234");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
                .andExpect(status().isCreated());
    }

    private Long checkout() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new AddToCartRequest(bookId, 1)))))
                .andExpect(status().isCreated());

        String json = mockMvc.perform(post("/api/orders/checkout"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json).get("orderId").asLong();
    }
}
