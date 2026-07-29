package com.bookstore.payment;

/**
 * Result of provider webhook parsing.
 * {@code ignored=true} means the event is acknowledged but needs no payment update
 * (unknown/unsupported event types) — callers should still return HTTP 200.
 */
public record ProviderWebhookResult(ProviderWebhookCommand command, boolean ignored) {

    public static ProviderWebhookResult of(ProviderWebhookCommand command) {
        return new ProviderWebhookResult(command, false);
    }

    public static ProviderWebhookResult ignoredEvent() {
        return new ProviderWebhookResult(null, true);
    }
}
