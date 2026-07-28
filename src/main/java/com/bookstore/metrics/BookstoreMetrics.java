package com.bookstore.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class BookstoreMetrics {

    private final MeterRegistry registry;

    public BookstoreMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void checkoutCompleted() {
        Counter.builder("bookstore.orders.checkout")
                .description("Orders created via checkout")
                .register(registry)
                .increment();
    }

    public void paymentIntentCreated(String provider) {
        Counter.builder("bookstore.payments.intent.created")
                .description("Payment intents created with a provider")
                .tag("provider", normalize(provider))
                .register(registry)
                .increment();
    }

    public void paymentWebhook(String provider, boolean success) {
        Counter.builder("bookstore.payments.webhook")
                .description("Payment webhooks processed")
                .tag("provider", normalize(provider))
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }

    public void unpaidOrderExpired() {
        Counter.builder("bookstore.orders.expired.unpaid")
                .description("Stale unpaid orders cancelled by the expiration job")
                .register(registry)
                .increment();
    }

    public void paymentRefunded(String provider) {
        Counter.builder("bookstore.payments.refund")
                .description("Successful payment refunds")
                .tag("provider", normalize(provider))
                .register(registry)
                .increment();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase();
    }
}
