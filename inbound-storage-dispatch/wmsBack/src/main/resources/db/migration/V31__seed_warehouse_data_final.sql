TRUNCATE TABLE
    transport_units, allocations, order_lines, orders,
    replenishments, tasks, inventory_history, stocks,
    locations, products, categories, users
    RESTART IDENTITY CASCADE;

INSERT INTO users (id, username, email, password, user_role, email_verified, is_active)
VALUES
    (1, 'dev', 'dev@isd.com', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_DEV', true, true),
    (2, 'supervisor', 'super@isd.com', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_SUPERVISOR', true, true),
    (3, 'operator', 'operator@isd.com', '$2a$12$Jgx.cGwjrw/ICdWSY4iYHuJ0eGKTRhfZ5IOO/tjrAtps/JkZ9J.vS', 'ROLE_OPERATOR', true, true),
    -- michael.scott -> password: Office%13
    (4, 'michael.scott', 'michael.scott@isd.com', '$2b$12$MfIbBa1ccvzcHJ7qkgxoxONeDzhSj3VN.N6GoyrukbYaFOZKomfcm', 'ROLE_SUPERVISOR', true, true),
    -- jim.halpert -> password: Dunder@38
    (5, 'jim.halpert', 'jim.halpert@isd.com', '$2b$12$lddj0PHxU3qWL2NV1KlCGuN/sLJ24YWQlm.On2USkocqlhZCwhY1i', 'ROLE_SUPERVISOR', true, true),
    -- pam.beesly -> password: Office!96
    (6, 'pam.beesly', 'pam.beesly@isd.com', '$2b$12$Sj0ztRyzCYVomhTmBWCVVu6HGwpoKq2Qr/XlM12gr50VD2.8UudiG', 'ROLE_OPERATOR', true, true),
    -- dwight.schrute -> password: WorldsBest#64
    (7, 'dwight.schrute', 'dwight.schrute@isd.com', '$2b$12$Xj4YCVOer/emSh67yuxFv.ThmWDK921ior6K0MASu1yTq9trMqUOq', 'ROLE_OPERATOR', true, true),
    -- stanley.hudson -> password: America$21
    (8, 'stanley.hudson', 'stanley.hudson@isd.com', '$2b$12$EUIHWo4v/atF1LdCHHXxf.c/G9nmvfNdJrrfK48Q89wPcmYC1oLIS', 'ROLE_SUPERVISOR', true, true),
    -- kevin.malone -> password: Dunder#74
    (9, 'kevin.malone', 'kevin.malone@isd.com', '$2b$12$I8ccluZbBfFleWzCyPXRoOytP0lE9pPp4r2UFOySfrqLk/Zlsheee', 'ROLE_OPERATOR', true, true),
    -- angela.martin -> password: BestBoss*35
    (10, 'angela.martin', 'angela.martin@isd.com', '$2b$12$Sq8uuwHMaqDzG776ixfKguxcKLex54QXOvHy5MGLM1XqYS2RYn1JW', 'ROLE_DEV', true, true),
    -- oscar.martinez -> password: Dunder%67
    (11, 'oscar.martinez', 'oscar.martinez@isd.com', '$2b$12$CifkinnNxJPiKLkmfgEuBe18bEMy4uq3f9HWrtN8sJli/JM5KTyu.', 'ROLE_OPERATOR', true, true),
    -- phyllis.vance -> password: America*30
    (12, 'phyllis.vance', 'phyllis.vance@isd.com', '$2b$12$xE4xBYFmEiFghX4tEx6st.fU5ENq8WabUt.HxCjSPvnKsB.ClE7QO', 'ROLE_OPERATOR', true, true),
    -- kelly.kapoor -> password: Scranton@45
    (13, 'kelly.kapoor', 'kelly.kapoor@isd.com', '$2b$12$.Nb0ZMYMdRXscObiPPUDKOllb/9zRCMYzdjE0fwbItEEVaWMPfM3u', 'ROLE_SUPERVISOR', true, true),
    -- toby.flenderson -> password: Dunder!53
    (14, 'toby.flenderson', 'toby.flenderson@isd.com', '$2b$12$/pQLchnod099waGEEpquau2uMjiurvr.TjFPnaeTQOVMDgj3EiQYG', 'ROLE_OPERATOR', true, true),
    -- creed.bratton -> password: Office!58
    (15, 'creed.bratton', 'creed.bratton@isd.com', '$2b$12$tHLmLzpS8FC1ClOSkWDtIOGKFwFVcO0QW1yT3tw9/BrjYhnzrJwYe', 'ROLE_OPERATOR', true, true),
    -- meredith.palmer -> password: Scranton%54
    (16, 'meredith.palmer', 'meredith.palmer@isd.com', '$2b$12$Jj/AFGZ0SHcAfQhy4BLzQuTFIFz8b99d59UMH13gMzIdM31dWxWom', 'ROLE_OPERATOR', true, true),
    -- ryan.howard -> password: America!68
    (17, 'ryan.howard', 'ryan.howard@isd.com', '$2b$12$fT0KoCncpB4shLEzCYonAuNExy9OJT5ONTUw/./jznRB1aDtsFlC2', 'ROLE_OPERATOR', true, true),
    -- darryl.philbin -> password: TheOffice%20
    (18, 'darryl.philbin', 'darryl.philbin@isd.com', '$2b$12$mu8koZ/uLu/sQNB6NRAau.Cemhi7dutDoRS62xYkpyCyNgMnunWC.', 'ROLE_OPERATOR', true, true),
    -- holly.flax -> password: WorldsBest$56
    (19, 'holly.flax', 'holly.flax@isd.com', '$2b$12$FC6M/w8YmCUnU08QCVUAi.c65r6.fO6UlZzthpjveV4D8P66e8.Ku', 'ROLE_DEV', true, true),
    -- jan.levinson -> password: Office$15
    (20, 'jan.levinson', 'jan.levinson@isd.com', '$2b$12$E7BhQ4/4ToACH1DTUpDH.OlAkWBQ95iBNQrajorvnOqqHF4875gQq', 'ROLE_SUPERVISOR', true, true);

INSERT INTO categories (id, name)
VALUES
    (1, 'Electronics'),
    (2, 'Food & Beverage'),
    (3, 'Household'),
    (4, 'Pharmacy'),
    (5, 'Warehouse Tools'),
    (6, 'Apparel & Uniforms'),
    (7, 'Automotive'),
    (8, 'Office Supplies'),
    (9, 'Chemicals & Safety'),
    (10, 'Packaging Materials');

INSERT INTO products (id, name, barcode, description, category_id, auto_replenish, min_threshold, replenish_qty)
VALUES
    (1, 'Yellow Notes', 'NOT-YLW-3X3', 'Yellow sticky notes 3x3 inches 100 sheets', 8, true, 30, 100),
    (2, 'Round Paper Clips', 'CLP-RND-100', 'Standard round paper clips box of 100', 8, true, 50, 200),
    (3, 'Neon Self-Stick Notes', 'NOT-NEON-4PK', 'Assorted neon self-stick notes 4 pack', 8, true, 20, 80),
    (4, 'Adhesive Film Index', 'IND-FLM-FLAGS', 'Colored adhesive film page index flags', 8, true, 25, 100),
    (5, 'Permanent Marker', 'MRK-PERM-BLK', 'Black permanent marker chisel tip', 8, true, 15, 50),
    (6, 'A4 Paper Box', 'PAPER-A4-001', 'A4 paper 500 sheets', 8, true, 50, 200),
    (7, 'Bottled Water 0.5L', 'WTR-0500-24', 'Water 24 bottles', 2, true, 100, 300),
    (8, 'Canned Beans 400g', 'BEANS-400G', 'Canned beans', 2, false, NULL, NULL),
    (9, 'Energy Drink 0.33L', 'ENG-DRK-33', 'Energy drink pack 12', 2, true, 20, 100),
    (10, 'Instant Coffee 200g', 'COF-INS-200', 'Instant coffee jar', 2, true, 15, 60),
    (11, 'Green Tea Pack', 'TEA-GRN-50', 'Green tea 50 bags', 2, false, NULL, NULL),
    (12, 'Paper Towels 12R', 'TOWELS-12R', 'Paper towels 12 rolls', 3, true, 30, 100),
    (13, 'Trash Bags 60L', 'BAG-TRSH-60', 'Trash bags 50pcs', 3, true, 40, 120),
    (14, 'Liquid Hand Soap 5L', 'SOAP-LIQ-5L', 'Hand soap refill', 3, false, NULL, NULL),
    (15, 'Microfiber Cloths', 'CLTH-MCF-10', 'Cleaning cloths 10pcs', 3, true, 25, 80),
    (16, 'Dishwashing Liquid', 'DISH-LIQ-1L', 'Dish soap 1L', 3, false, NULL, NULL),
    (17, 'Hand Sanitizer 500ml', 'SAN-500ML', 'Hand sanitizer', 4, true, 20, 80),
    (18, 'Paracetamol 500mg', 'MED-PCM-500', 'Painkiller 20 tabs', 4, true, 50, 200),
    (19, 'Ibuprofen 400mg', 'MED-IBU-400', 'Anti-inflammatory 20 tabs', 4, true, 40, 150),
    (20, 'First Aid Kit Small', 'FAK-SM-01', 'First aid kit', 4, false, NULL, NULL),
    (21, 'Medical Face Masks', 'MSK-MED-50', 'Face masks box 50', 4, true, 30, 100),
    (22, 'Wireless Scanner', 'SCN-WLS-001', 'Barcode scanner', 5, true, 5, 20),
    (23, 'Thermal Label Roll', 'LBL-100X150', 'Thermal labels', 10, true, 10, 50),
    (24, 'Work Gloves L', 'GLOVE-W-01', 'Work gloves size L', 6, false, NULL, NULL),
    (25, 'Pallet Wrap 500m', 'WRAP-PAL-500', 'Stretch film 500m', 10, true, 5, 20),
    (26, 'Heavy Duty Utility Knife', 'KNIF-UTL-01', 'Box cutter', 5, true, 15, 50),
    (27, 'Replacement Blades 10x', 'BLD-RET-10', 'Spare blades pack', 5, true, 20, 100),
    (28, 'Packing Tape Clear', 'TAPE-CLR-6R', 'Packing tape 6 rolls', 10, true, 40, 150),
    (29, 'Tape Dispenser Gun', 'GUN-TAPE-01', 'Tape dispenser', 5, false, NULL, NULL),
    (30, 'Steel Toe Boots M', 'BOOT-STL-M', 'Safety boots medium', 6, false, NULL, NULL);

INSERT INTO locations (id, barcode, name, zone, description, available, is_active)
VALUES
    (1, 'PICK-A-01', 'PICK-A-01', 'PICKING', 'Pick Face A01', true, true),
    (2, 'PICK-A-02', 'PICK-A-02', 'PICKING', 'Pick Face A02', true, true),
    (3, 'PICK-A-03', 'PICK-A-03', 'PICKING', 'Pick Face A03', true, true),
    (4, 'PICK-A-04', 'PICK-A-04', 'PICKING', 'Pick Face A04', true, true),
    (5, 'PICK-A-05', 'PICK-A-05', 'PICKING', 'Pick Face A05', true, true),
    (6, 'PICK-B-01', 'PICK-B-01', 'PICKING', 'Pick Face B01', true, true),
    (7, 'PICK-B-02', 'PICK-B-02', 'PICKING', 'Pick Face B02', true, true),
    (8, 'PICK-B-03', 'PICK-B-03', 'PICKING', 'Pick Face B03', true, true),
    (9, 'PICK-B-04', 'PICK-B-04', 'PICKING', 'Pick Face B04', true, true),
    (10, 'PICK-B-05', 'PICK-B-05', 'PICKING', 'Pick Face B05', true, true),
    (11, 'PICK-C-01', 'PICK-C-01', 'PICKING', 'Pick Face C01', true, true),
    (12, 'PICK-C-02', 'PICK-C-02', 'PICKING', 'Pick Face C02', true, true),
    (13, 'PICK-C-03', 'PICK-C-03', 'PICKING', 'Pick Face C03', true, true),
    (14, 'PICK-C-04', 'PICK-C-04', 'PICKING', 'Pick Face C04', true, true),
    (15, 'PICK-C-05', 'PICK-C-05', 'PICKING', 'Pick Face C05', true, true),
    (16, 'REPL-A-01', 'REPL-A-01', 'REPLENISHMENT', 'Storage Rack A01', true, true),
    (17, 'REPL-A-02', 'REPL-A-02', 'REPLENISHMENT', 'Storage Rack A02', true, true),
    (18, 'REPL-A-03', 'REPL-A-03', 'REPLENISHMENT', 'Storage Rack A03', true, true),
    (19, 'REPL-A-04', 'REPL-A-04', 'REPLENISHMENT', 'Storage Rack A04', true, true),
    (20, 'REPL-A-05', 'REPL-A-05', 'REPLENISHMENT', 'Storage Rack A05', true, true),
    (21, 'REPL-B-01', 'REPL-B-01', 'REPLENISHMENT', 'Storage Rack B01', true, true),
    (22, 'REPL-B-02', 'REPL-B-02', 'REPLENISHMENT', 'Storage Rack B02', true, true),
    (23, 'REPL-B-03', 'REPL-B-03', 'REPLENISHMENT', 'Storage Rack B03', true, true),
    (24, 'REPL-B-04', 'REPL-B-04', 'REPLENISHMENT', 'Storage Rack B04', true, true),
    (25, 'REPL-B-05', 'REPL-B-05', 'REPLENISHMENT', 'Storage Rack B05', true, true),
    (26, 'REPL-C-01', 'REPL-C-01', 'REPLENISHMENT', 'Storage Rack C01', true, true),
    (27, 'REPL-C-02', 'REPL-C-02', 'REPLENISHMENT', 'Storage Rack C02', true, true),
    (28, 'REPL-C-03', 'REPL-C-03', 'REPLENISHMENT', 'Storage Rack C03', true, true),
    (29, 'REPL-C-04', 'REPL-C-04', 'REPLENISHMENT', 'Storage Rack C04', true, true),
    (30, 'REPL-C-05', 'REPL-C-05', 'REPLENISHMENT', 'Storage Rack C05', true, true),
    (31, 'DISP-01', 'DISP-01', 'DISPATCH', 'Dispatch Lane 01', true, true),
    (32, 'DISP-02', 'DISP-02', 'DISPATCH', 'Dispatch Lane 02', true, true),
    (33, 'DISP-03', 'DISP-03', 'DISPATCH', 'Dispatch Lane 03', true, true),
    (34, 'DISP-04', 'DISP-04', 'DISPATCH', 'Dispatch Lane 04', true, true),
    (35, 'DISP-05', 'DISP-05', 'DISPATCH', 'Dispatch Lane 05', true, true),
    (36, 'DISP-06', 'DISP-06', 'DISPATCH', 'Dispatch Lane 06', true, true),
    (37, 'DISP-07', 'DISP-07', 'DISPATCH', 'Dispatch Lane 07', true, true),
    (38, 'DISP-08', 'DISP-08', 'DISPATCH', 'Dispatch Lane 08', true, true),
    (39, 'DISP-09', 'DISP-09', 'DISPATCH', 'Dispatch Lane 09', true, true),
    (40, 'DISP-10', 'DISP-10', 'DISPATCH', 'Dispatch Lane 10', true, true);

INSERT INTO transport_units (barcode, order_id, replenishment_id)
VALUES
    ('TU100001', NULL, NULL),
    ('TU100002', NULL, NULL),
    ('TU100003', NULL, NULL),
    ('TU100004', NULL, NULL),
    ('TU100005', NULL, NULL),
    ('TU100006', NULL, NULL),
    ('TU100007', NULL, NULL),
    ('TU100008', NULL, NULL),
    ('TU100009', NULL, NULL),
    ('TU100010', NULL, NULL),
    ('TU100011', NULL, NULL),
    ('TU100012', NULL, NULL),
    ('TU100013', NULL, NULL),
    ('TU100014', NULL, NULL),
    ('TU100015', NULL, NULL),
    ('TU100016', NULL, NULL),
    ('TU100017', NULL, NULL),
    ('TU100018', NULL, NULL),
    ('TU100019', NULL, NULL),
    ('TU100020', NULL, NULL),
    ('TU100021', NULL, NULL),
    ('TU100022', NULL, NULL),
    ('TU100023', NULL, NULL),
    ('TU100024', NULL, NULL),
    ('TU100025', NULL, NULL),
    ('TU100026', NULL, NULL),
    ('TU100027', NULL, NULL),
    ('TU100028', NULL, NULL),
    ('TU100029', NULL, NULL),
    ('TU100030', NULL, NULL),
    ('TU100031', NULL, NULL),
    ('TU100032', NULL, NULL),
    ('TU100033', NULL, NULL),
    ('TU100034', NULL, NULL),
    ('TU100035', NULL, NULL),
    ('TU100036', NULL, NULL),
    ('TU100037', NULL, NULL),
    ('TU100038', NULL, NULL),
    ('TU100039', NULL, NULL),
    ('TU100040', NULL, NULL),
    ('TU100041', NULL, NULL),
    ('TU100042', NULL, NULL),
    ('TU100043', NULL, NULL),
    ('TU100044', NULL, NULL),
    ('TU100045', NULL, NULL),
    ('TU100046', NULL, NULL),
    ('TU100047', NULL, NULL),
    ('TU100048', NULL, NULL),
    ('TU100049', NULL, NULL),
    ('TU100050', NULL, NULL);

SELECT setval('users_sequence', (SELECT MAX(id) FROM users));
SELECT setval('categories_sequence', (SELECT MAX(id) FROM categories));
SELECT setval('products_sequence', (SELECT MAX(id) FROM products));
SELECT setval('locations_sequence', (SELECT MAX(id) FROM locations));
SELECT setval('transport_units_sequence', (SELECT MAX(id) FROM transport_units));

ALTER TABLE replenishments ADD COLUMN logic_id VARCHAR(50);
