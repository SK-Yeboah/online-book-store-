package com.bookstore.payment;

public interface PaymentProvider {

    String name();
    ProviderIntentResult createIntent(ProviderIntentRequest request);
    ProviderWebhookResult parseAndVerifyWebhook(String rawBody, String signatureHeader);

    default boolean confirmPayment(String reference) {
        return true;
    }

    /**
     * Full refund when {@code amountMinorUnits} is null; otherwise partial refund in minor units.
     */
    default ProviderRefundResult refund(String reference, Long amountMinorUnits) {
        throw new UnsupportedOperationException("Refunds not supported by provider: " + name());
    }
}
