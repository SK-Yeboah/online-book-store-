package com.bookstore.service.serviceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.config.PaymentProperties;
import com.bookstore.dto.request.CreatePaymentIntentRequest;
import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.dto.response.PaymentIntentResponse;
import com.bookstore.dto.response.PaymentResponse;
import com.bookstore.entity.Order;
import com.bookstore.entity.Order.OrderStatus;
import com.bookstore.entity.Payment;
import com.bookstore.entity.Payment.PaymentStatus;
import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.exception.handler.ErrorCode;
import com.bookstore.metrics.BookstoreMetrics;
import com.bookstore.payment.PaymentProvider;
import com.bookstore.payment.PaymentProviderRegistry;
import com.bookstore.payment.ProviderIntentRequest;
import com.bookstore.payment.ProviderIntentResult;
import com.bookstore.payment.ProviderRefundResult;
import com.bookstore.payment.ProviderWebhookCommand;
import com.bookstore.payment.ProviderWebhookResult;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.PaymentRepository;
import com.bookstore.service.OrderService;
import com.bookstore.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Set<PaymentStatus> ACTIVE_STATUSES =
            EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.REQUIRES_ACTION);

    private static final Set<PaymentStatus> TERMINAL_STATUSES = EnumSet.of(
            PaymentStatus.SUCCEEDED,
            PaymentStatus.FAILED,
            PaymentStatus.CANCELLED,
            PaymentStatus.REFUNDED);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentProperties paymentProperties;
    private final OrderService orderService;
    private final BookstoreMetrics bookstoreMetrics;

    @Override
    @Transactional
    public PaymentIntentResponse createIntent(Long userId, String idempotencyKey, CreatePaymentIntentRequest request) {
        validateIdempotencyKey(idempotencyKey);

        var existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            return handleExistingIntent(existingByKey.get(), userId, request.getOrderId());
        }

        String providerName = resolveProviderName(request.getProvider());
        PaymentProvider provider = providerRegistry.getRequired(providerName);

        Order order = orderRepository.findByIdAndUser_Id(request.getOrderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("order", "id", request.getOrderId()));

        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            orderService.reopenForPayment(order.getId());
            order.setStatus(OrderStatus.PENDING_PAYMENT);
        } else if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BookstoreException(
                    ErrorCode.ORDER_NOT_PAYABLE.name(),
                    "Order is not awaiting payment",
                    HttpStatus.BAD_REQUEST);
        }

        Optional<Payment> active = paymentRepository
                .findFirstByOrder_IdAndStatusInOrderByCreatedAtDesc(order.getId(), ACTIVE_STATUSES);
        if (active.isPresent()) {
            log.info("Reusing active payment for orderId={} paymentId={} (new key ignored)",
                    order.getId(), active.get().getId());
            return PaymentIntentResponse.from(active.get());
        }

        Payment payment = paymentRepository.save(new Payment(
                order,
                provider.name(),
                order.getTotalAmount(),
                paymentProperties.getCurrency(),
                idempotencyKey));

        ProviderIntentResult result = provider.createIntent(new ProviderIntentRequest(
                order.getId(),
                order.getTotalAmount(),
                paymentProperties.getCurrency(),
                order.getUser().getEmail(),
                idempotencyKey,
                resolveCallbackUrl(request.getCallbackUrl())));

        payment.setProviderPaymentId(result.providerPaymentId());
        payment.setAuthorizationUrl(result.authorizationUrl());
        payment.setAccessCode(result.accessCode());
        payment.setStatus(PaymentStatus.REQUIRES_ACTION);

        bookstoreMetrics.paymentIntentCreated(provider.name());
        log.info("Payment intent created: paymentId={}, orderId={}, provider={}",
                payment.getId(), order.getId(), provider.name());

        return PaymentIntentResponse.from(payment);
    }

    @Override
    @Transactional
    public Optional<PaymentResponse> handleWebhook(String providerName, String rawBody, String signatureHeader) {
        PaymentProvider provider = providerRegistry.getRequired(providerName);

        ProviderWebhookResult parsed = provider.parseAndVerifyWebhook(rawBody, signatureHeader);
        if (parsed.ignored()) {
            log.info("Webhook ignored for provider={}", provider.name());
            return Optional.empty();
        }

        ProviderWebhookCommand command = parsed.command();

        Payment payment = paymentRepository.findByIdempotencyKey(command.reference())
                .orElseThrow(() -> new ResourceNotFoundException("payment", "reference", command.reference()));

        if (!payment.getProvider().equals(provider.name())) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                    "Webhook provider does not match payment provider",
                    HttpStatus.BAD_REQUEST);
        }

        if (TERMINAL_STATUSES.contains(payment.getStatus())) {
            log.info("Webhook replay for terminal paymentId={} status={}",
                    payment.getId(), payment.getStatus());
            return Optional.of(PaymentResponse.from(payment));
        }

        if (command.success() && !provider.confirmPayment(command.reference())) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                    "Payment could not be verified with provider",
                    HttpStatus.BAD_REQUEST);
        }

        if (command.success()) {
            applySuccess(payment, command.providerPaymentId());
        } else {
            applyFailure(payment, command.providerPaymentId());
        }

        bookstoreMetrics.paymentWebhook(provider.name(), command.success());
        log.info("Payment webhook processed: paymentId={}, provider={}, success={}",
                payment.getId(), provider.name(), command.success());

        return Optional.of(PaymentResponse.from(payment));
    }

    @Override
    @Transactional
    public PaymentResponse verifyByReference(Long userId, String reference) {
        Payment payment = paymentRepository.findByIdempotencyKey(reference)
                .orElseThrow(() -> new ResourceNotFoundException("payment", "reference", reference));

        if (!payment.getOrder().getUser().getId().equals(userId)) {
            throw new BookstoreException(
                    ErrorCode.ACCESS_DENIED.name(),
                    "You do not own this payment",
                    HttpStatus.FORBIDDEN);
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return PaymentResponse.from(payment);
        }

        if (payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BookstoreException(
                    ErrorCode.INVALID_PAYMENT_STATE.name(),
                    "Payment cannot be verified in status " + payment.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        PaymentProvider provider = providerRegistry.getRequired(payment.getProvider());
        if (!provider.confirmPayment(reference)) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_NOT_VERIFIED.name(),
                    "Provider has not confirmed this payment yet",
                    HttpStatus.CONFLICT);
        }

        applySuccess(payment, payment.getProviderPaymentId());
        log.info("Payment verified via client fallback: paymentId={}, reference={}",
                payment.getId(), reference);
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findByOrder(Long userId, Long orderId) {
        orderRepository.findByIdAndUser_Id(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("order", "id", orderId));

        Payment payment = paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("payment", "orderId", orderId));

        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public int expireStalePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(paymentProperties.getIntentTtlMinutes());
        Pageable limit = PageRequest.of(
                0,
                Math.max(1, paymentProperties.getExpirationBatchSize()),
                Sort.by("createdAt").ascending());

        List<Order> stale = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, cutoff, limit);

        int processed = 0;
        for (Order candidate : stale) {
            try {
                if (expireOne(candidate.getId())) {
                    bookstoreMetrics.unpaidOrderExpired();
                    processed++;
                }
            } catch (Exception ex) {
                log.warn("Expire skipped for orderId={}: {}", candidate.getId(), ex.getMessage());
            }
        }
        return processed;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> findAllAdmin(
            PaymentStatus status, String provider, Pageable pageable) {
        Specification<Payment> spec = buildAdminFilter(status, provider);
        Page<PaymentResponse> page = paymentRepository.findAll(spec, pageable).map(PaymentResponse::from);
        return PagedResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findByIdAdmin(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("payment", "id", paymentId));
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refund(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("payment", "id", paymentId));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentResponse.from(payment);
        }

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_NOT_REFUNDABLE.name(),
                    "Only SUCCEEDED payments can be refunded",
                    HttpStatus.BAD_REQUEST);
        }

        Order order = payment.getOrder();
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_NOT_REFUNDABLE.name(),
                    "Refunds are only allowed for CONFIRMED orders",
                    HttpStatus.BAD_REQUEST);
        }

        PaymentProvider provider = providerRegistry.getRequired(payment.getProvider());
        try {
            ProviderRefundResult result = provider.refund(payment.getIdempotencyKey(), null);
            log.info("Provider refund accepted: paymentId={}, refundId={}, status={}",
                    payment.getId(), result.refundId(), result.status());
        } catch (BookstoreException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Refund failed for paymentId={}", payment.getId(), ex);
            throw new BookstoreException(
                    ErrorCode.REFUND_FAILED.name(),
                    "Provider refund failed",
                    HttpStatus.BAD_GATEWAY);
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        orderService.updateStatus(order.getId(), new UpdateOrderStatusRequest(OrderStatus.CANCELLED));
        bookstoreMetrics.paymentRefunded(payment.getProvider());
        log.info("Payment refunded: paymentId={}, orderId={}", payment.getId(), order.getId());
        return PaymentResponse.from(payment);
    }

    private static Specification<Payment> buildAdminFilter(PaymentStatus status, String provider) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (provider != null && !provider.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("provider")), provider.toLowerCase()));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private boolean expireOne(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }

        Optional<Payment> active = paymentRepository
                .findFirstByOrder_IdAndStatusInOrderByCreatedAtDesc(orderId, ACTIVE_STATUSES);

        if (active.isPresent()) {
            Payment payment = active.get();
            PaymentProvider provider = providerRegistry.getRequired(payment.getProvider());

            boolean paid;
            try {
                paid = provider.confirmPayment(payment.getIdempotencyKey());
            } catch (Exception ex) {
                log.warn("Provider confirm failed for orderId={}, skip expire", orderId, ex);
                return false;
            }

            if (paid) {
                applySuccess(payment, payment.getProviderPaymentId());
                log.info("Stale order confirmed via provider verify: orderId={}", orderId);
                return true;
            }

            payment.setStatus(PaymentStatus.CANCELLED);
            orderService.cancelUnpaid(orderId);
            log.info("Stale unpaid order expired: orderId={}, paymentId={}", orderId, payment.getId());
            return true;
        }

        orderService.cancelUnpaid(orderId);
        log.info("Stale order with no payment intent expired: orderId={}", orderId);
        return true;
    }

    private void applySuccess(Payment payment, String providerPaymentId) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }
        if (providerPaymentId != null && !providerPaymentId.isBlank()) {
            payment.setProviderPaymentId(providerPaymentId);
        }
        payment.setStatus(PaymentStatus.SUCCEEDED);
        orderService.markPaid(payment.getOrder().getId());
    }

    private void applyFailure(Payment payment, String providerPaymentId) {
        if (TERMINAL_STATUSES.contains(payment.getStatus())) {
            return;
        }
        if (providerPaymentId != null && !providerPaymentId.isBlank()) {
            payment.setProviderPaymentId(providerPaymentId);
        }
        payment.setStatus(PaymentStatus.FAILED);
        orderService.markPaymentFailed(payment.getOrder().getId());
    }

    private PaymentIntentResponse handleExistingIntent(Payment payment, Long userId, Long orderId) {
        if (!payment.getOrder().getUser().getId().equals(userId)
                || !payment.getOrder().getId().equals(orderId)) {
            throw new BookstoreException(
                    ErrorCode.INVALID_PAYMENT_STATE.name(),
                    "Idempotency key belongs to a different order",
                    HttpStatus.BAD_REQUEST);
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_ALREADY_SUCCEEDED.name(),
                    "Payment already succeeded",
                    HttpStatus.BAD_REQUEST);
        }

        if (payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BookstoreException(
                    ErrorCode.INVALID_PAYMENT_STATE.name(),
                    "Payment ended in " + payment.getStatus()
                            + "; use a new Idempotency-Key to retry",
                    HttpStatus.BAD_REQUEST);
        }

        return PaymentIntentResponse.from(payment);
    }

    private String resolveProviderName(String requestProvider) {
        if (requestProvider != null && !requestProvider.isBlank()) {
            return requestProvider;
        }
        return paymentProperties.getProvider();
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BookstoreException(
                    ErrorCode.VALIDATION_FAILED.name(),
                    "Idempotency-Key header is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (idempotencyKey.length() > 100) {
            throw new BookstoreException(
                    ErrorCode.VALIDATION_FAILED.name(),
                    "Idempotency-Key must be at most 100 characters",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveCallbackUrl(String requestCallbackUrl) {
        if (requestCallbackUrl != null && !requestCallbackUrl.isBlank()) {
            return requestCallbackUrl;
        }
        return paymentProperties.getCallbackUrl();
    }
}
