package com.bookstore.dto.response;


import com.bookstore.entity.Payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentResponse {

    private Long paymentId;
    private Long orderId;
    private String reference;
    private String authorizationUrl;
    private String accessCode;
    private Payment.PaymentStatus status;
    private String provider;

    public static PaymentIntentResponse from(Payment payment) {
        return new PaymentIntentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getIdempotencyKey(),
                payment.getAuthorizationUrl(),
                payment.getAccessCode(),
                payment.getStatus(),
                payment.getProvider());
    }
}