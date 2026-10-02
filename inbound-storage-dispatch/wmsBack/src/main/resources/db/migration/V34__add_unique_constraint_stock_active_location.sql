-- Enforce cell/location monopoly: only one active product stock can exist per location at any time.
CREATE UNIQUE INDEX IF NOT EXISTS uk_stocks_active_location
    ON stocks (location_id)
    WHERE available = true;
