package com.bookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

@Data
@ConfigurationProperties(prefix = "paystack")
public class PaystackProperties {

    private String secretKey;
    private String publicKey;
    private String baseUrl = "https://api.paystack.co";
}
