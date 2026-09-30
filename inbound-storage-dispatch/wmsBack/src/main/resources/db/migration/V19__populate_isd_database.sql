TRUNCATE TABLE processes, replenishments, order_lines, orders, tasks, inventory_history, stocks, locations, products, categories, users RESTART IDENTITY CASCADE;

INSERT INTO users (id, username, email, password, user_role, email_verified, is_active)
VALUES
    (1, 'dev', 'dev@isd.com', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_DEV', true,true),
    (2, 'supervisor', 'super@isd.com', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_SUPERVISOR', true,true),
    (3, 'operator', 'operator@isd.com', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_OPERATOR', true,true);

INSERT INTO categories (id, name)
VALUES
    (1, 'Electronics'),
    (2, 'Office Supplies'),
    (3, 'Warehouse Equipment');

INSERT INTO products (id, name, barcode, description, category_id)
VALUES
    (1, 'Laptop Pro 15', 'LAP-PRO-001', 'High performance laptop for development', 1),
    (2, 'Wireless Mouse', 'MOUSE-WLS-001', 'Ergonomic optical mouse', 1),
    (3, 'A4 Paper Box', 'PAPER-A4-001', 'Box of 500 sheets A4 printer paper', 2),
    (4, 'Scanner Symbol', 'SCN-SYM-001', 'Barcode scanner for terminal', 3),
    (5, 'Work Gloves', 'GLOVE-W-01', 'Protective warehouse gloves', 3);

INSERT INTO locations (id, barcode, zone, description, available)
VALUES
    (1, 'PICK-A-01', 'PICKING', 'Pick Rack A, Level 1', true),
    (2, 'PICK-A-02', 'PICKING', 'Pick Rack A, Level 2', true),
    (3, 'PICK-B-01', 'PICKING', 'Pick Rack B, Level 1', true),
    (4, 'REPL-A-01', 'REPLENISHMENT', 'Bulk Pallet Storage A1', true),
    (5, 'REPL-A-02', 'REPLENISHMENT', 'Bulk Pallet Storage A2', true),
    (6, 'DISP-01', 'DISPATCH', 'Dispatch Staging Area 1', true);

INSERT INTO stocks (id, product_id, location_id, quantity, quantity_reserved)
VALUES
    (1, 1, 4, 100, 0),
    (2, 1, 1, 5, 0),
    (3, 3, 5, 500, 0),
    (4, 5, 3, 50, 0);

INSERT INTO inventory_history (
    id,
    product_id,
    barcode,
    altered_quantity,
    quantity_after_change,
    source_location_id,
    destination_location_id,
    operation_type,
    timestamp,
    user_id
)
VALUES
    (1, 1, 'LAP-PRO-001', 100, 100, NULL, 4, 'ADD_STOCK', CURRENT_TIMESTAMP - INTERVAL '3 days', 2),
    (2, 1, 'LAP-PRO-001', 5, 95, 4, 1, 'MOVE_STOCK', CURRENT_TIMESTAMP - INTERVAL '2 days', 3),
    (3, 3, 'PAPER-A4-001', 500, 500, NULL, 5, 'ADD_STOCK', CURRENT_TIMESTAMP - INTERVAL '2 days', 2),
    (4, 5, 'GLOVE-W-01', 50, 50, NULL, 3, 'ADD_STOCK', CURRENT_TIMESTAMP - INTERVAL '1 day', 2),
    (5, 3, 'PAPER-A4-001', -2, 498, 5, NULL, 'REMOVE_STOCK', CURRENT_TIMESTAMP - INTERVAL '5 hours', 2);

INSERT INTO tasks (id, supervisor_id, task_type, requested_quantity, status)
VALUES
    (1, 2, 'REPLENISHMENT', 20, 'CREATED'),
    (2, 2, 'PICKING_ORDER', 2, 'CREATED');

INSERT INTO replenishments (id, task_id, product_id, requested_quantity, status, destination_location_id)
VALUES
    (1, 1, 3, 20, 'CREATED', 2);

INSERT INTO orders (id, logic_id, status, destination_location_id)
VALUES
    (1, 'ORD-2026-0001', 'CREATED', 6);

INSERT INTO order_lines (id, order_id, task_id, product_id, requested_quantity, status)
VALUES
    (1, 1, 2, 1, 2, 'CREATED');


SELECT setval('users_sequence', (SELECT MAX(id) FROM users));
SELECT setval('categories_sequence', (SELECT MAX(id) FROM categories));
SELECT setval('products_sequence', (SELECT MAX(id) FROM products));
SELECT setval('locations_sequence', (SELECT MAX(id) FROM locations));
SELECT setval('stocks_sequence', (SELECT MAX(id) FROM stocks));
SELECT setval('inventory_sequence', (SELECT MAX(id) FROM inventory_history));
SELECT setval('tasks_sequence', (SELECT MAX(id) FROM tasks));
SELECT setval('replenishments_sequence', (SELECT MAX(id) FROM replenishments));
SELECT setval('orders_sequence', (SELECT MAX(id) FROM orders));
SELECT setval('order_lines_sequence', (SELECT MAX(id) FROM order_lines));
