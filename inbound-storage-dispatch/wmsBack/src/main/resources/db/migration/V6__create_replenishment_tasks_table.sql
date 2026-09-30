CREATE SEQUENCE replenishment_tasks_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS replenishment_tasks (
    id bigint DEFAULT nextval('replenishment_tasks_sequence') PRIMARY KEY,
    product_id bigint REFERENCES products(id),
    operator_id bigint REFERENCES users(id),
    requested_quantity integer NOT NULL,
    status varchar(30) NOT NULL,
    source_location_id bigint REFERENCES locations(id),
    destination_location_id bigint REFERENCES locations(id),
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
);
