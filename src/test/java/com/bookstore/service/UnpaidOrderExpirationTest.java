package com.bookstore.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.CreatePaymentIntentRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.entity.Book;
import com.bookstore.entity.Order.OrderStatus;
import com.bookstore.entity.Payment.PaymentStatus;
import com.bookstore.payment.MockPaymentProvider;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Expire unpaid orders")
class UnpaidOrderExpirationTest {

    private static final String USERNAME = "expireuser";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookRepository bookRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentService paymentService;
    @Autowired MockPaymentProvider mockPaymentProvider;
    @Autowired EntityManager entityManager;

    private Long bookId;
    private Long orderId;
    private int initialStock;

    @BeforeEach
    void setUp() throws Exception {
        mockPaymentProvider.resetConfirmBehavior();
        registerUser();
        Book book = bookRepository.save(new Book(
                "Expire Book", "Author", "Fiction", "978-0000000199",
                12.50, 5, "Description"));
        bookId = book.getId();
        initialStock = book.getStockQuantity();
        orderId = checkout();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("unpaid stale order — cancels payment and restores stock")
    void expire_unpaid_cancelsAndRestoresStock() throws Exception {
        createIntent("expire_key_unpaid");
        mockPaymentProvider.setConfirmSucceeds(false);
        backdateOrder(orderId, LocalDateTime.now().minusHours(2));

        assertThat(bookRepository.findById(bookId).orElseThrow().getStockQuantity())
                .isEqualTo(initialStock - 1);

        int processed = paymentService.expireStalePendingOrders();

        assertThat(processed).isEqualTo(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CANCELLED);
        assertThat(bookRepository.findById(bookId).orElseThrow().getStockQuantity())
                .isEqualTo(initialStock);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("provider already paid — confirms order, keeps stock reserved")
    void expire_whenProviderPaid_confirmsOrder() throws Exception {
        createIntent("expire_key_paid");
        mockPaymentProvider.setConfirmSucceeds(true);
        backdateOrder(orderId, LocalDateTime.now().minusHours(2));

        int processed = paymentService.expireStalePendingOrders();

        assertThat(processed).isEqualTo(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(bookRepository.findById(bookId).orElseThrow().getStockQuantity())
                .isEqualTo(initialStock - 1);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("fresh order within TTL — skipped")
    void expire_skipsFreshOrders() throws Exception {
        createIntent("expire_key_fresh");
        mockPaymentProvider.setConfirmSucceeds(false);

        int processed = paymentService.expireStalePendingOrders();

        assertThat(processed).isEqualTo(0);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("no payment intent — cancels order and restores stock")
    void expire_noPaymentRow_cancelsOrder() {
        backdateOrder(orderId, LocalDateTime.now().minusHours(2));

        int processed = paymentService.expireStalePendingOrders();

        assertThat(processed).isEqualTo(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentRepository.findByOrder_IdOrderByCreatedAtDesc(orderId)).isEmpty();
        assertThat(bookRepository.findById(bookId).orElseThrow().getStockQuantity())
                .isEqualTo(initialStock);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("provider error — leaves order pending")
    void expire_providerError_leavesPending() throws Exception {
        createIntent("expire_key_error");
        mockPaymentProvider.setConfirmThrows(true);
        backdateOrder(orderId, LocalDateTime.now().minusHours(2));

        int processed = paymentService.expireStalePendingOrders();

        assertThat(processed).isEqualTo(0);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(bookRepository.findById(bookId).orElseThrow().getStockQuantity())
                .isEqualTo(initialStock - 1);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    @DisplayName("second expire run is idempotent")
    void expire_idempotentSecondRun() throws Exception {
        createIntent("expire_key_idem");
        mockPaymentProvider.setConfirmSucceeds(false);
        backdateOrder(orderId, LocalDateTime.now().minusHours(2));

        assertThat(paymentService.expireStalePendingOrders()).isEqualTo(1);
        assertThat(paymentService.expireStalePendingOrders()).isEqualTo(0);
        assertThat(bookRepository.findById(bookId).orElseThrow().getStockQuantity())
                .isEqualTo(initialStock);
    }

    private void backdateOrder(Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE orders SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private void createIntent(String key) throws Exception {
        mockMvc.perform(post("/api/payments/intent")
                        .header("Idempotency-Key", key)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                new CreatePaymentIntentRequest(orderId, null, null)))))
                .andExpect(status().isOk());
    }

    private void registerUser() throws Exception {
        RegisterRequest req = new RegisterRequest(USERNAME, "expire@example.com", "password1234");
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
