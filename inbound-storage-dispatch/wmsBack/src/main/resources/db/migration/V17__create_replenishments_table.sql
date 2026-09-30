CREATE SEQUENCE replenishments_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS replenishments(
    id bigint DEFAULT nextval('replenishments_sequence') PRIMARY KEY,
    task_id bigint REFERENCES tasks(id) NOT NULL,
    product_id bigint REFERENCES products(id) NOT NULL,
    requested_quantity integer NOT NULL,
    status varchar(30) DEFAULT 'CREATED' NOT NULL,
    destination_location_id bigint REFERENCES locations(id) NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
    );