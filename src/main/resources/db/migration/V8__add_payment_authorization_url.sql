ALTER TABLE `payments`
    ADD COLUMN `authorization_url` VARCHAR(512) NULL AFTER `idempotency_key`,
    ADD COLUMN `access_code` VARCHAR(100) NULL AFTER `authorization_url`;