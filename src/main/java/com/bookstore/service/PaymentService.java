package com.bookstore.service;

import com.bookstore.dto.request.CreatePaymentIntentRequest;
import com.bookstore.dto.response.PaymentIntentResponse;
import com.bookstore.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentIntentResponse createIntent(Long userId, String idempotencyKey, CreatePaymentIntentRequest request);
    PaymentResponse handleWebhook(String providerName, String rawBody, String signatureHeader);
    PaymentResponse findByOrder(Long userId, Long orderId);
    
} 