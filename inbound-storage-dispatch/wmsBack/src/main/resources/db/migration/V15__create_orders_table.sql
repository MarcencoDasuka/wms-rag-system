CREATE SEQUENCE orders_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS orders(
    id bigint DEFAULT nextval('orders_sequence') PRIMARY KEY,
    logic_id varchar(50) NOT NULL UNIQUE,
    status varchar(30) DEFAULT 'CREATED' NOT NULL,
    destination_location_id bigint REFERENCES locations(id) NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
);
