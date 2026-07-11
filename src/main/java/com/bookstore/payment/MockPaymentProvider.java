package com.bookstore.payment;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bookstore.config.PaymentProperties;
import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.handler.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock")
public class MockPaymentProvider implements PaymentProvider {

    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ProviderIntentResult createIntent(ProviderIntentRequest request) {
        return new ProviderIntentResult(
                "mock_" + UUID.randomUUID(),
                "https://mock.pay/checkout/" + request.reference(),
                "mock_access_" + request.reference(),
                null);
    }

    @Override
    public ProviderWebhookResult parseAndVerifyWebhook(String rawBody, String signatureHeader) {
        String expected = paymentProperties.getWebhookSecret();
        if (expected == null || !expected.equals(signatureHeader)) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                    "Invalid mock webhook secret",
                    HttpStatus.BAD_REQUEST);
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            return new ProviderWebhookResult(new ProviderWebhookCommand(
                    root.path("providerPaymentId").asText("mock_evt"),
                    root.path("reference").asText(),
                    root.path("success").asBoolean(false),
                    root.path("eventId").asText("mock.event")));
        } catch (Exception ex) {
            log.error("Mock webhook parse error", ex);
            throw new BookstoreException(
                    ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                    "Invalid mock webhook payload",
                    HttpStatus.BAD_REQUEST);
        }
    }
}