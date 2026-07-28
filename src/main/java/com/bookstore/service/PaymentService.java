package com.bookstore.service;

import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.bookstore.dto.request.CreatePaymentIntentRequest;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.dto.response.PaymentIntentResponse;
import com.bookstore.dto.response.PaymentResponse;
import com.bookstore.entity.Payment;

public interface PaymentService {

    PaymentIntentResponse createIntent(Long userId, String idempotencyKey, CreatePaymentIntentRequest request);

    /**
     * Process a provider webhook. Empty means the event was acknowledged but ignored
     * (unsupported event type) — callers should still return HTTP 200.
     */
    Optional<PaymentResponse> handleWebhook(String providerName, String rawBody, String signatureHeader);

    PaymentResponse verifyByReference(Long userId, String reference);

    PaymentResponse findByOrder(Long userId, Long orderId);

    /**
     * Confirm-or-cancel stale PENDING_PAYMENT orders. Returns how many orders were
     * confirmed or expired in this run.
     */
    int expireStalePendingOrders();

    PagedResponse<PaymentResponse> findAllAdmin(
            Payment.PaymentStatus status, String provider, Pageable pageable);

    PaymentResponse findByIdAdmin(Long paymentId);

    /** Full refund for a SUCCEEDED payment on a CONFIRMED order. */
    PaymentResponse refund(Long paymentId);
}
