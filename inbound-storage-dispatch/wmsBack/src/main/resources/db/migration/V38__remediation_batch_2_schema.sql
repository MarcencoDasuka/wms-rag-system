-- Flyway Migration V38: Remediation Batch 2 Schema Updates
-- Resolves DEF-11 (Replenishment destination partial unique index)
-- Resolves DEF-03 & DEF-04 (Adds created_by to orders and replenishments for lifecycle object-level authorization)

-- 1. DEF-11: Clean up any legacy duplicate active replenishments for destination_location_id before adding unique index.
-- If multiple active replenishments exist for the same destination location, keep the earliest one and cancel the rest.
UPDATE replenishments
SET status = 'CANCELED'
WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS')
  AND id NOT IN (
      SELECT MIN(id)
      FROM replenishments
      WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS')
      GROUP BY destination_location_id
  );

-- Drop old composite index that allowed multiple different products to target the same destination location
DROP INDEX IF EXISTS uk_active_replenishment_product_destination;

-- Create partial unique index ensuring only ONE active replenishment exists per destination location
CREATE UNIQUE INDEX IF NOT EXISTS uk_active_replenishment_destination
    ON replenishments (destination_location_id)
    WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS');

-- 2. DEF-03 & DEF-04: Add created_by column to orders and replenishments for lifecycle authorization
ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE replenishments ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);

-- Backfill created_by for existing replenishments from their task supervisors if available
UPDATE replenishments r
SET created_by = u.username
FROM tasks t
JOIN users u ON t.supervisor_id = u.id
WHERE r.task_id = t.id AND r.created_by IS NULL;

-- Backfill created_by for existing orders from their order line task supervisors if available
UPDATE orders o
SET created_by = u.username
FROM order_lines ol
JOIN tasks t ON ol.task_id = t.id
JOIN users u ON t.supervisor_id = u.id
WHERE ol.order_id = o.id AND o.created_by IS NULL;

-- Default any remaining unassigned historical records to 'supervisor'
UPDATE orders SET created_by = 'supervisor' WHERE created_by IS NULL;
UPDATE replenishments SET created_by = 'supervisor' WHERE created_by IS NULL;

-- Create performance indexes for created_by lookup
CREATE INDEX IF NOT EXISTS idx_orders_created_by ON orders (created_by);
CREATE INDEX IF NOT EXISTS idx_replenishments_created_by ON replenishments (created_by);
