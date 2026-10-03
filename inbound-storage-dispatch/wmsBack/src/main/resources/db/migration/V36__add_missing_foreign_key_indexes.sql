-- Flyway migration V36: Add indexes for foreign key columns to optimize JOINs and prevent full table locks on CASCADE/DELETE/UPDATE operations

-- 1. products
CREATE INDEX IF NOT EXISTS idx_products_category_id
    ON products (category_id);

-- 2. stocks
CREATE INDEX IF NOT EXISTS idx_stocks_location_id
    ON stocks (location_id);

-- 3. orders
CREATE INDEX IF NOT EXISTS idx_orders_destination_location_id
    ON orders (destination_location_id);

-- 4. order_lines
CREATE INDEX IF NOT EXISTS idx_order_lines_order_id
    ON order_lines (order_id);

CREATE INDEX IF NOT EXISTS idx_order_lines_product_id
    ON order_lines (product_id);

CREATE INDEX IF NOT EXISTS idx_order_lines_task_id
    ON order_lines (task_id);

-- 5. replenishments
CREATE INDEX IF NOT EXISTS idx_replenishments_destination_location_id
    ON replenishments (destination_location_id);

CREATE INDEX IF NOT EXISTS idx_replenishments_product_id
    ON replenishments (product_id);

CREATE INDEX IF NOT EXISTS idx_replenishments_task_id
    ON replenishments (task_id);

-- 6. tasks
CREATE INDEX IF NOT EXISTS idx_tasks_operator_id
    ON tasks (operator_id);

CREATE INDEX IF NOT EXISTS idx_tasks_supervisor_id
    ON tasks (supervisor_id);

-- 7. allocations
CREATE INDEX IF NOT EXISTS idx_allocations_stock_id
    ON allocations (stock_id);

CREATE INDEX IF NOT EXISTS idx_allocations_task_id
    ON allocations (task_id);

-- 8. transport_units
CREATE INDEX IF NOT EXISTS idx_transport_units_order_id
    ON transport_units (order_id);

CREATE INDEX IF NOT EXISTS idx_transport_units_replenishment_id
    ON transport_units (replenishment_id);

-- 9. inventory_history
CREATE INDEX IF NOT EXISTS idx_inventory_history_destination_location_id
    ON inventory_history (destination_location_id);

CREATE INDEX IF NOT EXISTS idx_inventory_history_source_location_id
    ON inventory_history (source_location_id);

CREATE INDEX IF NOT EXISTS idx_inventory_history_product_id
    ON inventory_history (product_id);

CREATE INDEX IF NOT EXISTS idx_inventory_history_user_id
    ON inventory_history (user_id);
