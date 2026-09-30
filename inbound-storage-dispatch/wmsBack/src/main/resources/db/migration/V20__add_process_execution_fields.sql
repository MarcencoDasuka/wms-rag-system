ALTER TABLE processes
    ALTER COLUMN status TYPE varchar(20),
    ADD COLUMN IF NOT EXISTS source_location_scanned boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS product_scanned boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS picked_quantity integer;
