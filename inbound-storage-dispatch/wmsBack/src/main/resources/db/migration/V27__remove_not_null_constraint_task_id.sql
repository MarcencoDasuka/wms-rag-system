ALTER TABLE order_lines
    ALTER COLUMN task_id DROP NOT NULL;

ALTER TABLE replenishments
    ALTER COLUMN task_id DROP NOT NULL;
