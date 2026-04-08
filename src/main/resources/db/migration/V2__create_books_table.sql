CREATE TABLE `books` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `title`          VARCHAR(255)  NOT NULL,
    `author`         VARCHAR(255)  NOT NULL,
    `category`       VARCHAR(255)  NOT NULL,
    `isbn`           VARCHAR(255)  NOT NULL,
    `price`          DOUBLE        NOT NULL,
    `stock_quantity` INT           NOT NULL,
    `description`    VARCHAR(2000),
    `created_at`     DATETIME(6)   NOT NULL,
    `updated_at`     DATETIME(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_books_isbn` (`isbn`),
    INDEX `idx_books_category` (`category`),
    INDEX `idx_books_author` (`author`)
);
