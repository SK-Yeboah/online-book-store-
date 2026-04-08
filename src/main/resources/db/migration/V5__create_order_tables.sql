CREATE TABLE `orders` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT      NOT NULL,
    `total_amount` DOUBLE      NOT NULL,
    `status`       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `created_at`   DATETIME(6) NOT NULL,
    `updated_at`   DATETIME(6),
    PRIMARY KEY (`id`),
    INDEX `idx_orders_user_id` (`user_id`),
    INDEX `idx_orders_status` (`status`),
    CONSTRAINT `fk_orders_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
);

-- price_at_purchase captures the book price at the moment of purchase,
-- ensuring historical orders are unaffected by future price changes.
CREATE TABLE `order_items` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `order_id`          BIGINT      NOT NULL,
    `book_id`           BIGINT      NOT NULL,
    `quantity`          INT         NOT NULL,
    `price_at_purchase` DOUBLE      NOT NULL,
    `created_at`        DATETIME(6) NOT NULL,
    `updated_at`        DATETIME(6),
    PRIMARY KEY (`id`),
    INDEX `idx_order_items_order_id` (`order_id`),
    INDEX `idx_order_items_book_id` (`book_id`),
    CONSTRAINT `fk_order_items_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_order_items_book`
        FOREIGN KEY (`book_id`) REFERENCES `books` (`id`) ON DELETE RESTRICT
);
