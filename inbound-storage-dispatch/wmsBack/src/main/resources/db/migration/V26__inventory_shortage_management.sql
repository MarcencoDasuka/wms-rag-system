ALTER TABLE inventory_history
    ADD COLUMN IF NOT EXISTS previous_quantity integer,
    ADD COLUMN IF NOT EXISTS adjustment_reason varchar(50),
    ADD COLUMN IF NOT EXISTS comment varchar(500);

ALTER TABLE order_lines
    ADD COLUMN IF NOT EXISTS shortage_quantity integer NOT NULL DEFAULT 0;
