package com.bookstore.payment;

public interface PaymentProvider {

    String name();
    ProviderIntentResult createIntent(ProviderIntentRequest request);
    ProviderWebhookResult parseAndVerifyWebhook(String rawBody, String signatureHeader);

    default boolean confirmPayment(String reference){
        return true;
    }
}
