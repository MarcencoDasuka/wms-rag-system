-- Flyway migration V35: Enforce logic_id uniqueness across replenishments and orders

-- 1. Ensure any null or blank logic_id in replenishments gets populated with a unique identifier
UPDATE replenishments
SET logic_id = 'REPL-' || UPPER(SUBSTRING(MD5(RANDOM()::TEXT || id::TEXT), 1, 8))
WHERE logic_id IS NULL OR TRIM(logic_id) = '';

-- 2. Deduplicate any existing duplicate logic_ids in replenishments (case-insensitive) prior to constraint
DO $$
DECLARE
    dup RECORD;
BEGIN
    FOR dup IN
        SELECT id, logic_id
        FROM (
            SELECT id, logic_id,
                   ROW_NUMBER() OVER (PARTITION BY LOWER(logic_id) ORDER BY id) as rn
            FROM replenishments
            WHERE logic_id IS NOT NULL
        ) t
        WHERE t.rn > 1
    LOOP
        UPDATE replenishments
        SET logic_id = dup.logic_id || '-' || UPPER(SUBSTRING(MD5(RANDOM()::TEXT || dup.id::TEXT), 1, 4))
        WHERE id = dup.id;
    END LOOP;
END $$;

-- 3. Enforce NOT NULL on replenishments.logic_id
ALTER TABLE replenishments
    ALTER COLUMN logic_id SET NOT NULL;

-- 4. Add unique constraint on replenishments (logic_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_replenishments_logic_id'
    ) THEN
        ALTER TABLE replenishments
            ADD CONSTRAINT uk_replenishments_logic_id UNIQUE (logic_id);
    END IF;
END $$;

-- 5. Add case-insensitive unique index on replenishments (LOWER(logic_id))
CREATE UNIQUE INDEX IF NOT EXISTS uk_replenishments_logic_id_lower
    ON replenishments (LOWER(logic_id));

-- 6. Deduplicate any existing duplicate logic_ids in orders (case-insensitive) prior to index
DO $$
DECLARE
    dup RECORD;
BEGIN
    FOR dup IN
        SELECT id, logic_id
        FROM (
            SELECT id, logic_id,
                   ROW_NUMBER() OVER (PARTITION BY LOWER(logic_id) ORDER BY id) as rn
            FROM orders
            WHERE logic_id IS NOT NULL
        ) t
        WHERE t.rn > 1
    LOOP
        UPDATE orders
        SET logic_id = dup.logic_id || '-' || UPPER(SUBSTRING(MD5(RANDOM()::TEXT || dup.id::TEXT), 1, 4))
        WHERE id = dup.id;
    END LOOP;
END $$;

-- 7. Add case-insensitive unique index on orders (LOWER(logic_id))
CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_logic_id_lower
    ON orders (LOWER(logic_id));
