CREATE TABLE `carts` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6),
    PRIMARY KEY (`id`),
    INDEX `idx_carts_user_id` (`user_id`),
    CONSTRAINT `fk_carts_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
);

CREATE TABLE `cart_items` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `cart_id`    BIGINT      NOT NULL,
    `book_id`    BIGINT      NOT NULL,
    `quantity`   INT         NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6),
    PRIMARY KEY (`id`),
    INDEX `idx_cart_items_cart_id` (`cart_id`),
    INDEX `idx_cart_items_book_id` (`book_id`),
    CONSTRAINT `fk_cart_items_cart`
        FOREIGN KEY (`cart_id`) REFERENCES `carts` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_cart_items_book`
        FOREIGN KEY (`book_id`) REFERENCES `books` (`id`) ON DELETE CASCADE
);
