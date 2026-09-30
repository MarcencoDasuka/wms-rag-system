ALTER TABLE stocks
    ADD COLUMN quantity_reserved Integer NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0 AND quantity_reserved <= quantity);