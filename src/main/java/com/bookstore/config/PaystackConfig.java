package com.bookstore.config;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "payment.provider", havingValue = "paystack")
public class PaystackConfig {

    @Bean
    RestClient payStackRestClient(PaystackProperties properties) {
        String secretKey = Objects.requireNonNull(
                properties.getSecretKey(),
                "paystack.secret-key must be set when payment.provider=paystack");
        String baseUrl = Objects.requireNonNull(
                properties.getBaseUrl(),
                "paystack.base-url must be set");

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}