-- Flyway Migration V39: Remediation Batch 3 Schema Updates
-- Resolves DEF-09 & DEF-10: Optimistic locking version columns on orders and replenishments
-- Resolves DEF-20: Case-insensitive unique functional expression indexes on users (username & email)

-- 1. DEF-09 & DEF-10: Add version column to orders and replenishments for optimistic concurrency control
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE replenishments ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- 2. DEF-20: Add case-insensitive functional unique expression indexes on users
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username_lower ON users (LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower ON users (LOWER(email));
