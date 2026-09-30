ALTER TABLE stocks
    ADD CONSTRAINT uk_stocks_product_location UNIQUE (product_id, location_id);
