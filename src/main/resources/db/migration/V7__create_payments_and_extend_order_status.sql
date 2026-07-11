-- Extend order status for payment lifecycle
ALTER TABLE `orders`
    MODIFY COLUMN `status` ENUM(
        'PENDING',
        'PENDING_PAYMENT',
        'PAYMENT_FAILED',
        'CONFIRMED',
        'SHIPPED',
        'DELIVERED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'PENDING';

CREATE TABLE `payments` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`             BIGINT       NOT NULL,
    `provider`             VARCHAR(50)  NOT NULL,
    `provider_payment_id`  VARCHAR(255) NULL,
    `amount`               DOUBLE       NOT NULL,
    `currency`             VARCHAR(10)  NOT NULL DEFAULT 'USD',
    `status`               ENUM(
                              'INITIATED',
                              'REQUIRES_ACTION',
                              'SUCCEEDED',
                              'FAILED',
                              'CANCELLED',
                              'REFUNDED'
                           ) NOT NULL DEFAULT 'INITIATED',
    `idempotency_key`      VARCHAR(100) NOT NULL,
    `created_at`           DATETIME(6)  NOT NULL,
    `updated_at`           DATETIME(6)  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payments_idempotency_key` (`idempotency_key`),
    INDEX `idx_payments_order_id` (`order_id`),
    INDEX `idx_payments_provider_payment_id` (`provider_payment_id`),
    CONSTRAINT `fk_payments_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT
);