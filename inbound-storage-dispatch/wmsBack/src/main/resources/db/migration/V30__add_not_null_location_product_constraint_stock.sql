ALTER TABLE stocks
    ALTER COLUMN location_id SET NOT NULL;

ALTER TABLE stocks
    ALTER COLUMN product_id SET NOT NULL;

ALTER TABLE stocks
    ADD COLUMN available BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE stocks
    ADD CONSTRAINT date_constraint CHECK(expiration_date >= manufacture_date)
