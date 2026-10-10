-- ===================================================================
-- V40: Reset Operational State & Seed Structured Testing Inventory
--
-- Objective:
-- 1. Cascade cleanup of all operational entities (orders, order_lines,
--    replenishments, tasks, allocations, transport_units links, history).
-- 2. Expand warehouse layout with additional picking and bulk storage locations.
-- 3. Seed comprehensive stocks across products 1..30 designed specifically
--    for reproducible testing of Order Shortages and Replenishment Shortages.
-- ===================================================================

-- 1. Unlink transport units
UPDATE transport_units
SET order_id = NULL,
    replenishment_id = NULL;

-- 2. Delete operational allocations and order lines
DELETE FROM allocations;
DELETE FROM order_lines;

-- 3. Delete orders and replenishments
DELETE FROM orders;
DELETE FROM replenishments;

-- 4. Delete operational tasks
DELETE FROM tasks;

-- 5. Reset inventory history audit log
DELETE FROM inventory_history;

-- 6. Delete all current stocks
DELETE FROM stocks;

-- 7. Reset sequences for cleared tables
ALTER SEQUENCE orders_sequence RESTART WITH 1;
ALTER SEQUENCE order_lines_sequence RESTART WITH 1;
ALTER SEQUENCE replenishments_sequence RESTART WITH 1;
ALTER SEQUENCE tasks_sequence RESTART WITH 1;
ALTER SEQUENCE allocations_sequence RESTART WITH 1;
ALTER SEQUENCE inventory_sequence RESTART WITH 1;
ALTER SEQUENCE stocks_sequence RESTART WITH 1;

-- 8. Seed additional locations (D, E, F aisles)
INSERT INTO locations (id, barcode, name, zone, description, available, is_active)
VALUES
    (41, 'PICK-D-01', 'PICK-D-01', 'PICKING', 'Pick Face D01', true, true),
    (42, 'PICK-D-02', 'PICK-D-02', 'PICKING', 'Pick Face D02', true, true),
    (43, 'PICK-D-03', 'PICK-D-03', 'PICKING', 'Pick Face D03', true, true),
    (44, 'PICK-D-04', 'PICK-D-04', 'PICKING', 'Pick Face D04', true, true),
    (45, 'PICK-D-05', 'PICK-D-05', 'PICKING', 'Pick Face D05', true, true),
    (46, 'PICK-E-01', 'PICK-E-01', 'PICKING', 'Pick Face E01', true, true),
    (47, 'PICK-E-02', 'PICK-E-02', 'PICKING', 'Pick Face E02', true, true),
    (48, 'PICK-E-03', 'PICK-E-03', 'PICKING', 'Pick Face E03', true, true),
    (49, 'PICK-E-04', 'PICK-E-04', 'PICKING', 'Pick Face E04', true, true),
    (50, 'PICK-E-05', 'PICK-E-05', 'PICKING', 'Pick Face E05', true, true),
    (51, 'PICK-F-01', 'PICK-F-01', 'PICKING', 'Pick Face F01', true, true),
    (52, 'PICK-F-02', 'PICK-F-02', 'PICKING', 'Pick Face F02', true, true),
    (53, 'PICK-F-03', 'PICK-F-03', 'PICKING', 'Pick Face F03', true, true),
    (54, 'PICK-F-04', 'PICK-F-04', 'PICKING', 'Pick Face F04', true, true),
    (55, 'PICK-F-05', 'PICK-F-05', 'PICKING', 'Pick Face F05', true, true),
    (56, 'REPL-D-01', 'REPL-D-01', 'REPLENISHMENT', 'Storage Rack D01', true, true),
    (57, 'REPL-D-02', 'REPL-D-02', 'REPLENISHMENT', 'Storage Rack D02', true, true),
    (58, 'REPL-D-03', 'REPL-D-03', 'REPLENISHMENT', 'Storage Rack D03', true, true),
    (59, 'REPL-D-04', 'REPL-D-04', 'REPLENISHMENT', 'Storage Rack D04', true, true),
    (60, 'REPL-D-05', 'REPL-D-05', 'REPLENISHMENT', 'Storage Rack D05', true, true),
    (61, 'REPL-E-01', 'REPL-E-01', 'REPLENISHMENT', 'Storage Rack E01', true, true),
    (62, 'REPL-E-02', 'REPL-E-02', 'REPLENISHMENT', 'Storage Rack E02', true, true),
    (63, 'REPL-E-03', 'REPL-E-03', 'REPLENISHMENT', 'Storage Rack E03', true, true),
    (64, 'REPL-E-04', 'REPL-E-04', 'REPLENISHMENT', 'Storage Rack E04', true, true),
    (65, 'REPL-E-05', 'REPL-E-05', 'REPLENISHMENT', 'Storage Rack E05', true, true),
    (66, 'REPL-F-01', 'REPL-F-01', 'REPLENISHMENT', 'Storage Rack F01', true, true),
    (67, 'REPL-F-02', 'REPL-F-02', 'REPLENISHMENT', 'Storage Rack F02', true, true),
    (68, 'REPL-F-03', 'REPL-F-03', 'REPLENISHMENT', 'Storage Rack F03', true, true),
    (69, 'REPL-F-04', 'REPL-F-04', 'REPLENISHMENT', 'Storage Rack F04', true, true),
    (70, 'REPL-F-05', 'REPL-F-05', 'REPLENISHMENT', 'Storage Rack F05', true, true)
ON CONFLICT (id) DO NOTHING;

SELECT setval('locations_sequence', (SELECT COALESCE(MAX(id), 1) FROM locations));

-- 9. Seed Comprehensive Stocks Grid
-- Constraints satisfied:
--   - UNIQUE(product_id, location_id)
--   - UNIQUE(location_id) WHERE available = true (cell monopoly)
--   - quantity >= 0
--   - quantity_reserved = 0
--   - expiration_date >= manufacture_date

INSERT INTO stocks (product_id, location_id, quantity, quantity_reserved, available, manufacture_date, expiration_date, version)
VALUES
    -- -----------------------------------------------------------------
    -- GROUP A: Happy Path (Abundant stock in both Picking & Replenishment)
    -- Products 1..8
    -- -----------------------------------------------------------------
    (1,  1,  100, 0, true, '2026-01-01', '2029-01-01', 0),
    (1,  16, 500, 0, true, '2026-01-01', '2029-01-01', 0),
    (2,  2,  250, 0, true, '2026-01-01', '2029-01-01', 0),
    (2,  17, 1000, 0, true, '2026-01-01', '2029-01-01', 0),
    (3,  3,  80,  0, true, '2026-01-01', '2029-01-01', 0),
    (3,  18, 400, 0, true, '2026-01-01', '2029-01-01', 0),
    (4,  4,  100, 0, true, '2026-01-01', '2029-01-01', 0),
    (4,  19, 500, 0, true, '2026-01-01', '2029-01-01', 0),
    (5,  5,  60,  0, true, '2026-01-01', '2029-01-01', 0),
    (5,  20, 300, 0, true, '2026-01-01', '2029-01-01', 0),
    (6,  6,  150, 0, true, '2026-01-01', '2029-01-01', 0),
    (6,  21, 800, 0, true, '2026-01-01', '2029-01-01', 0),
    (7,  7,  200, 0, true, '2026-01-01', '2027-12-31', 0),
    (7,  22, 1000, 0, true, '2026-01-01', '2027-12-31', 0),
    (8,  8,  120, 0, true, '2026-01-01', '2028-12-31', 0),
    (8,  23, 600, 0, true, '2026-01-01', '2028-12-31', 0),

    -- -----------------------------------------------------------------
    -- GROUP B: Picking Shortage -> Triggers Replenishment
    -- (Low stock in Picking, Plenty of stock in Replenishment)
    -- Products 9..16
    -- -----------------------------------------------------------------
    (9,  9,  2,   0, true, '2026-01-01', '2027-12-31', 0),
    (9,  24, 350, 0, true, '2026-01-01', '2027-12-31', 0),
    (10, 10, 3,   0, true, '2026-01-01', '2028-06-30', 0),
    (10, 25, 250, 0, true, '2026-01-01', '2028-06-30', 0),
    (11, 26, 400, 0, true, '2026-01-01', '2028-06-30', 0),
    (12, 12, 4,   0, true, '2026-01-01', '2029-01-01', 0),
    (12, 27, 300, 0, true, '2026-01-01', '2029-01-01', 0),
    (13, 13, 5,   0, true, '2026-01-01', '2029-01-01', 0),
    (13, 28, 350, 0, true, '2026-01-01', '2029-01-01', 0),
    (14, 29, 200, 0, true, '2026-01-01', '2028-12-31', 0),
    (15, 15, 2,   0, true, '2026-01-01', '2029-01-01', 0),
    (15, 30, 300, 0, true, '2026-01-01', '2029-01-01', 0),
    (16, 56, 250, 0, true, '2026-01-01', '2028-12-31', 0),

    -- -----------------------------------------------------------------
    -- GROUP C: Replenishment Shortage / Total Warehouse Stockout
    -- (Near-Zero in Replenishment for 18, 20; 17, 19, 21, 22 are total stockout)
    -- Products 17..22
    -- -----------------------------------------------------------------
    (18, 58, 2,   0, true, '2026-01-01', '2029-01-01', 0),
    (20, 60, 1,   0, true, '2026-01-01', '2029-01-01', 0),

    -- -----------------------------------------------------------------
    -- GROUP D: Multi-location Picking & ShortageResolver Testing
    -- (Items across 2 picking faces with different exp dates + backup storage)
    -- Products 23..26
    -- -----------------------------------------------------------------
    (23, 48, 15,  0, true, '2026-01-15', '2027-06-30', 0),
    (23, 49, 35,  0, true, '2026-06-15', '2028-06-30', 0),
    (23, 63, 200, 0, true, '2026-06-15', '2028-06-30', 0),
    (24, 50, 10,  0, true, '2026-02-01', '2029-01-01', 0),
    (24, 51, 40,  0, true, '2026-05-01', '2029-01-01', 0),
    (24, 64, 200, 0, true, '2026-05-01', '2029-01-01', 0),
    (25, 52, 5,   0, true, '2026-01-01', '2029-01-01', 0),
    (25, 53, 25,  0, true, '2026-04-01', '2029-01-01', 0),
    (25, 65, 100, 0, true, '2026-04-01', '2029-01-01', 0),
    (26, 54, 8,   0, true, '2026-01-01', '2029-01-01', 0),
    (26, 55, 30,  0, true, '2026-03-01', '2029-01-01', 0),
    (26, 66, 150, 0, true, '2026-03-01', '2029-01-01', 0);

-- Products 27..30 remain unstocked in warehouse to test zero-record catalog items.

-- 10. Sync sequence
SELECT setval('stocks_sequence', (SELECT COALESCE(MAX(id), 1) FROM stocks));
