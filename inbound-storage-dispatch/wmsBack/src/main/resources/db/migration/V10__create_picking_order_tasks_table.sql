CREATE SEQUENCE picking_order_tasks_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS picking_order_tasks (
    id bigint DEFAULT nextval('picking_order_tasks_sequence') PRIMARY KEY,
    picking_order_id bigint REFERENCES picking_orders(id),
    operator_id bigint REFERENCES users(id),
    location_id bigint REFERENCES locations(id),
    task_type varchar(50),
    status varchar(30) NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
);
