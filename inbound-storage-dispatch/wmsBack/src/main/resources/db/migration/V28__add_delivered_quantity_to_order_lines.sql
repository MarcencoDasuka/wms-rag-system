ALTER TABLE order_lines
    ADD COLUMN IF NOT EXISTS delivered_quantity integer NOT NULL DEFAULT 0;
