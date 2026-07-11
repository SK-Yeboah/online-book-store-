package com.bookstore.service.serviceImpl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.config.PaymentProperties;
import com.bookstore.dto.request.CreatePaymentIntentRequest;
import com.bookstore.dto.response.PaymentIntentResponse;
import com.bookstore.dto.response.PaymentResponse;
import com.bookstore.entity.Order;
import com.bookstore.entity.Order.OrderStatus;
import com.bookstore.entity.Payment;
import com.bookstore.entity.Payment.PaymentStatus;
import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.exception.handler.ErrorCode;
import com.bookstore.payment.PaymentProvider;
import com.bookstore.payment.PaymentProviderRegistry;
import com.bookstore.payment.ProviderIntentRequest;
import com.bookstore.payment.ProviderIntentResult;
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

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentProperties paymentProperties;
    private final OrderService orderService;

    @Override
    @Transactional
    public PaymentIntentResponse createIntent(Long userId, String idempotencyKey, CreatePaymentIntentRequest request) {
        validateIdempotencyKey(idempotencyKey);

        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return handleExistingIntent(existing.get(), userId, request.getOrderId());
        }

        String providerName = resolveProviderName(request.getProvider());
        PaymentProvider provider = providerRegistry.getRequired(providerName);

        Order order = orderRepository.findByIdAndUser_Id(request.getOrderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("order", "id", request.getOrderId()));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BookstoreException(
                    ErrorCode.ORDER_NOT_PAYABLE.name(),
                    "Order is not awaiting payment",
                    HttpStatus.BAD_REQUEST);
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

        log.info("Payment intent created: paymentId={}, orderId={}, provider={}",
                payment.getId(), order.getId(), provider.name());

        return PaymentIntentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse handleWebhook(String providerName, String rawBody, String signatureHeader) {
        PaymentProvider provider = providerRegistry.getRequired(providerName);

        ProviderWebhookResult parsed = provider.parseAndVerifyWebhook(rawBody, signatureHeader);
        ProviderWebhookCommand command = parsed.command();

        Payment payment = paymentRepository.findByIdempotencyKey(command.reference())
                .orElseThrow(() -> new ResourceNotFoundException("payment", "reference", command.reference()));

        if (!payment.getProvider().equals(provider.name())) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                    "Webhook provider does not match payment provider",
                    HttpStatus.BAD_REQUEST);
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return PaymentResponse.from(payment);
        }

        if (command.success() && !provider.confirmPayment(command.reference())) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                    "Payment could not be verified with provider",
                    HttpStatus.BAD_REQUEST);
        }

        payment.setProviderPaymentId(command.providerPaymentId());

        if (command.success()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            orderService.markPaid(payment.getOrder().getId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            orderService.markPaymentFailed(payment.getOrder().getId());
        }

        log.info("Payment webhook processed: paymentId={}, provider={}, success={}",
                payment.getId(), provider.name(), command.success());

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