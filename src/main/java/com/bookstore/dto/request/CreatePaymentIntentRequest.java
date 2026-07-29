package com.bookstore.dto.request;


import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentIntentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    private String callbackUrl;

    private String provider;
}