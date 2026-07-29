package com.bookstore.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.bookstore.entity.Payment;
import com.bookstore.entity.Payment.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    List<Payment> findByOrder_IdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findFirstByOrder_IdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findFirstByOrder_IdAndStatusInOrderByCreatedAtDesc(
            Long orderId, Collection<PaymentStatus> statuses);
}
