package com.bookstore.payment;

/** Normalized webhook after provider verification. */
public record ProviderWebhookCommand(
        String providerPaymentId,
        String reference,
        boolean success,
        String eventId
) {}