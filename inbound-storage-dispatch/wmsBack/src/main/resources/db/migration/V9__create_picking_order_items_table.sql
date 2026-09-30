CREATE SEQUENCE picking_order_items_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS picking_order_items (
    id bigint DEFAULT nextval('picking_order_items_sequence') PRIMARY KEY,
    picking_order_id bigint REFERENCES picking_orders(id),
    product_id bigint REFERENCES products(id),
    required_quantity integer NOT NULL,
    confirmed_quantity integer DEFAULT 0 NOT NULL
);
