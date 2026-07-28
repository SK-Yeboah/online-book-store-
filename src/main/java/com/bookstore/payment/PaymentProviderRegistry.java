package com.bookstore.payment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.handler.ErrorCode;

@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providerList) {
        Map<String, PaymentProvider> byName = new HashMap<>();
        for (PaymentProvider provider : providerList) {
            Objects.requireNonNull(provider, "PaymentProvider must not be null");
            byName.put(provider.name(), provider);
        }
        this.providers = Map.copyOf(byName);
    }

    public PaymentProvider getRequired(String name) {
        PaymentProvider provider = providers.get(name);
        if (provider == null) {
            throw new BookstoreException(
                    ErrorCode.BAD_REQUEST.name(),
                    "Unknown payment provider: " + name,
                    HttpStatus.BAD_REQUEST);
        }
        return provider;
    }
}