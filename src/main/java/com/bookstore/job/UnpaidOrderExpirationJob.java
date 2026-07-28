package com.bookstore.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bookstore.config.PaymentProperties;
import com.bookstore.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidOrderExpirationJob {

    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;

    @Scheduled(fixedDelayString = "${payment.expiration-fixed-delay-ms:300000}")
    public void run() {
        if (!paymentProperties.isExpirationEnabled()) {
            return;
        }
        int processed = paymentService.expireStalePendingOrders();
        if (processed > 0) {
            log.info("Expired/confirmed {} stale pending-payment order(s)", processed);
        }
    }
}
