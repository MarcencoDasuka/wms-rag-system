INSERT INTO users (id, username, email, password, user_role)
VALUES
    (1, 'dev', 'dev@isd.local', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_DEV'),
    (2, 'supervisor', 'supervisor@isd.local', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_SUPERVISOR'),
    (3, 'operator', 'operator@isd.local', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_OPERATOR');

INSERT INTO categories (id, name)
VALUES
    (1, 'Electronics'),
    (2, 'Food'),
    (3, 'Household'),
    (4, 'Pharmacy');

INSERT INTO products (id, name, barcode, description, category_id)
VALUES
    (1, 'Wireless Scanner', 'SCN-WLS-001', 'Handheld barcode scanner for warehouse operations', 1),
    (2, 'Thermal Label Roll', 'LBL-100X150', '100x150 mm thermal labels for shipping and inventory', 1),
    (3, 'Bottled Water 0.5L', 'WTR-0500-24', 'Packaged drinking water, 24 bottles per box', 2),
    (4, 'Canned Beans', 'BEANS-400G', 'Long shelf-life canned beans, 400 g', 2),
    (5, 'Paper Towels', 'TOWELS-12R', 'Absorbent paper towels, 12 rolls per case', 3),
    (6, 'Hand Sanitizer', 'SAN-500ML', 'Alcohol-based hand sanitizer, 500 ml bottle', 4);

INSERT INTO locations (id, barcode, zone, description, available)
VALUES
    (1, 'A-01-01', 'A', 'Receiving buffer rack A-01-01', true),
    (2, 'A-01-02', 'A', 'Receiving buffer rack A-01-02', true),
    (3, 'B-02-01', 'B', 'Bulk storage rack B-02-01', true),
    (4, 'B-02-02', 'B', 'Bulk storage rack B-02-02', true),
    (5, 'PICK-01', 'PICK', 'Primary picking location 01', true),
    (6, 'PICK-02', 'PICK', 'Primary picking location 02', true),
    (7, 'QC-01', 'QC', 'Quality control hold location', false),
    (8, 'SHIP-01', 'SHIP', 'Outbound staging lane 01', true);

INSERT INTO stocks (id, product_id, location_id, quantity, manufacture_date, expiration_date, version)
VALUES
    (1, 1, 3, 25, DATE '2026-01-15', NULL, 0),
    (2, 2, 4, 180, DATE '2026-02-01', NULL, 0),
    (3, 3, 1, 320, DATE '2026-04-10', DATE '2027-04-10', 0),
    (4, 4, 2, 210, DATE '2026-03-20', DATE '2028-03-20', 0),
    (5, 5, 5, 75, DATE '2026-02-25', NULL, 0),
    (6, 6, 6, 140, DATE '2026-05-05', DATE '2028-05-05', 0),
    (7, 3, 5, 48, DATE '2026-04-10', DATE '2027-04-10', 0);

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
    (1, 1, 'SCN-WLS-001', 25, 25, NULL, 3, 'ADD_STOCK', TIMESTAMPTZ '2026-06-01 09:00:00+00', 2),
    (2, 2, 'LBL-100X150', 180, 180, NULL, 4, 'ADD_STOCK', TIMESTAMPTZ '2026-06-01 09:15:00+00', 2),
    (3, 3, 'WTR-0500-24', 320, 320, NULL, 1, 'ADD_STOCK', TIMESTAMPTZ '2026-06-01 09:30:00+00', 3),
    (4, 4, 'BEANS-400G', 210, 210, NULL, 2, 'ADD_STOCK', TIMESTAMPTZ '2026-06-01 09:45:00+00', 3),
    (5, 5, 'TOWELS-12R', 75, 75, NULL, 5, 'ADD_STOCK', TIMESTAMPTZ '2026-06-01 10:00:00+00', 3),
    (6, 6, 'SAN-500ML', 140, 140, NULL, 6, 'ADD_STOCK', TIMESTAMPTZ '2026-06-01 10:15:00+00', 3),
    (7, 3, 'WTR-0500-24', 48, 48, 1, 5, 'ADD_STOCK', TIMESTAMPTZ '2026-06-01 11:00:00+00', 3);

INSERT INTO replenishment_tasks (
    id,
    product_id,
    operator_id,
    requested_quantity,
    status,
    source_location_id,
    destination_location_id,
    created_at
)
VALUES
    (1, 3, 3, 48, 'COMPLETED', 1, 5, TIMESTAMPTZ '2026-06-01 10:45:00+00'),
    (2, 6, 3, 30, 'PENDING', 6, 8, TIMESTAMPTZ '2026-06-02 08:30:00+00');

INSERT INTO picking_orders (id, order_number, timestamp, status)
VALUES
    (1, 'PO-2026-0001', TIMESTAMPTZ '2026-06-02 12:00:00+00', 'OPEN'),
    (2, 'PO-2026-0002', TIMESTAMPTZ '2026-06-02 13:30:00+00', 'IN_PROGRESS'),
    (3, 'PO-2026-0003', TIMESTAMPTZ '2026-06-03 09:00:00+00', 'COMPLETED');

INSERT INTO picking_order_items (id, picking_order_id, product_id, required_quantity, confirmed_quantity)
VALUES
    (1, 1, 3, 24, 0),
    (2, 1, 5, 6, 0),
    (3, 2, 6, 20, 5),
    (4, 2, 2, 30, 10),
    (5, 3, 4, 12, 12);

INSERT INTO picking_order_tasks (
    id,
    picking_order_id,
    operator_id,
    location_id,
    task_type,
    status,
    created_at
)
VALUES
    (1, 1, 3, 5, 'PICK', 'PENDING', TIMESTAMPTZ '2026-06-02 12:05:00+00'),
    (2, 2, 3, 6, 'PICK', 'IN_PROGRESS', TIMESTAMPTZ '2026-06-02 13:35:00+00'),
    (3, 3, 3, 2, 'PICK', 'COMPLETED', TIMESTAMPTZ '2026-06-03 09:05:00+00');

SELECT setval('users_sequence', (SELECT MAX(id) FROM users));
SELECT setval('categories_sequence', (SELECT MAX(id) FROM categories));
SELECT setval('products_sequence', (SELECT MAX(id) FROM products));
SELECT setval('locations_sequence', (SELECT MAX(id) FROM locations));
SELECT setval('stocks_sequence', (SELECT MAX(id) FROM stocks));
SELECT setval('inventory_sequence', (SELECT MAX(id) FROM inventory_history));
SELECT setval('replenishment_tasks_sequence', (SELECT MAX(id) FROM replenishment_tasks));
SELECT setval('picking_orders_sequence', (SELECT MAX(id) FROM picking_orders));
SELECT setval('picking_order_items_sequence', (SELECT MAX(id) FROM picking_order_items));
SELECT setval('picking_order_tasks_sequence', (SELECT MAX(id) FROM picking_order_tasks));
