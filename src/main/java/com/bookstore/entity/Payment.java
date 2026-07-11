package com.bookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(name = "payments")
public class Payment extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false)
    private Double amount;

    @NotBlank
    @Column(nullable = false, length = 10)
    private String currency = "USD";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @NotBlank
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "authorization_url", length = 512)
    private String authorizationUrl;

    @Column(name = "access_code", length = 100)
    private String accessCode;


    public Payment(Order order, String provider, Double amount, String currency, String idempotencyKey) {
        this.order = order;
        this.provider = provider;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.INITIATED;
    }

    public enum PaymentStatus {
        INITIATED,
        REQUIRES_ACTION,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        REFUNDED
    }
}
