CREATE SEQUENCE inventory_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS inventory_history (
    id bigint DEFAULT nextval('inventory_sequence') PRIMARY KEY,
    product_id bigint REFERENCES products(id),
    barcode varchar(100) NOT NULL,
    altered_quantity integer NOT NULL,
    quantity_after_change integer NOT NULL,
    source_location_id bigint REFERENCES locations(id),
    destination_location_id bigint REFERENCES locations(id),
    operation_type varchar(50) NOT NULL,
    timestamp timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id bigint REFERENCES users(id)
);
