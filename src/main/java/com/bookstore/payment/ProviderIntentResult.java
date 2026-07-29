package com.bookstore.payment;

public record ProviderIntentResult (
    String providerPaymentId,
    String authorizationUrl,
    String accessCode,
    String clientSecret
) {
    
}
