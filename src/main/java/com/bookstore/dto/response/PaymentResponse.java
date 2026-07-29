package com.bookstore.dto.response;

import java.time.LocalDateTime;

import com.bookstore.entity.Payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long paymentId;
    private Long orderId;
    private String reference;
    private String providerPaymentId;
    private Double amount;
    private String currency;
    private Payment.PaymentStatus status;
    private String provider;
    private LocalDateTime createdAt;

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getIdempotencyKey(),
                payment.getProviderPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getCreatedAt());
    }
}