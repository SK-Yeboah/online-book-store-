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
    
}
