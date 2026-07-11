package com.bookstore.payment;

public record ProviderIntentRequest(
    Long orderId,
    Double amount, 
    String currency,
    String email,
    String reference,
    String callbackUrl
) {

   
    
}
