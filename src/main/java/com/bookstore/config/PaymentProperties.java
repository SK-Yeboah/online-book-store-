package com.bookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private String provider = "mock";
    private String currency = "GHS";
    private String callbackUrl;
    private String webhookSecret;

    /** How long a PENDING_PAYMENT order may hold stock before expiration. */
    private int intentTtlMinutes = 45;

    private boolean expirationEnabled = true;
    private int expirationBatchSize = 50;
    private long expirationFixedDelayMs = 300_000L;
}
