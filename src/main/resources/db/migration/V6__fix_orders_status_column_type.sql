-- Hibernate @Enumerated(EnumType.STRING) on MySQL requires ENUM column type for schema validation.
-- V5 created status as VARCHAR(20); this migration aligns it with the entity definition.
ALTER TABLE `orders`
    MODIFY COLUMN `status` ENUM('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING';
