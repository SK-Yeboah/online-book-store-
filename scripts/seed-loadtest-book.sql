-- High-stock book for local / k6 end-to-end load tests.
-- Idempotent: safe to re-run (unique ISBN).

INSERT INTO `books` (
    `title`,
    `author`,
    `category`,
    `isbn`,
    `price`,
    `stock_quantity`,
    `description`,
    `created_at`,
    `updated_at`
)
SELECT
    'Load Test Book',
    'Bookstore QA',
    'Fiction',
    'ISBN-LOADTEST-001',
    29.99,
    1000000,
    'Seeded for end-to-end payment load testing',
    NOW(6),
    NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM `books` WHERE `isbn` = 'ISBN-LOADTEST-001'
);
