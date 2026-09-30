CREATE SEQUENCE picking_orders_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS picking_orders (
    id bigint DEFAULT nextval('picking_orders_sequence') PRIMARY KEY,
    order_number varchar(50) NOT NULL UNIQUE,
    timestamp timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status varchar(30) NOT NULL
);
