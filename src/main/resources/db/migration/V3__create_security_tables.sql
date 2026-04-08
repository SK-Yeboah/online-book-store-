CREATE TABLE `login_attempts` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `username`        VARCHAR(255) NOT NULL,
    `failed_attempts` INT          NOT NULL DEFAULT 0,
    `locked_until`    DATETIME(6),
    `last_failed_at`  DATETIME(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_login_attempts_username` (`username`),
    INDEX `idx_username` (`username`)
);

CREATE TABLE `refresh_tokens` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `token`      VARCHAR(255) NOT NULL,
    `user_id`    BIGINT       NOT NULL,
    `expires_at` DATETIME(6)  NOT NULL,
    `used`       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refresh_tokens_token` (`token`),
    INDEX `idx_refresh_tokens_user_id` (`user_id`),
    CONSTRAINT `fk_refresh_tokens_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
);
