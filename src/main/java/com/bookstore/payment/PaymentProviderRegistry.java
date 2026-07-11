package com.bookstore.payment;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.handler.ErrorCode;

@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(PaymentProvider::name, Function.identity()));
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