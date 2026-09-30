DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'locations'
          AND column_name = 'location_code'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'locations'
          AND column_name = 'barcode'
    ) THEN
        ALTER TABLE locations RENAME COLUMN location_code TO barcode;
    END IF;
END $$;

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS name VARCHAR(50);

UPDATE locations
SET name = barcode
WHERE name IS NULL
   OR name = 'name';

ALTER TABLE locations
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE locations
    ADD CONSTRAINT locations_name_key UNIQUE (name);
