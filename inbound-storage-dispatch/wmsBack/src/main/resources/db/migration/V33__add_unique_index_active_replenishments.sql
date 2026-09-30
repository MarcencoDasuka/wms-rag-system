CREATE UNIQUE INDEX uk_active_replenishment_product_destination
    ON replenishments (product_id, destination_location_id)
    WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS');
