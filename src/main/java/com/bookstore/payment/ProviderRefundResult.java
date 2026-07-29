package com.bookstore.payment;

/** Result of a provider refund call. */
public record ProviderRefundResult(String refundId, String status) {}
