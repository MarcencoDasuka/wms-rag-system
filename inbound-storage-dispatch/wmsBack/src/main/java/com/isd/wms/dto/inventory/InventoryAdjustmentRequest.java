package com.isd.wms.dto.inventory;

import com.isd.wms.enums.InventoryAdjustmentReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InventoryAdjustmentRequest(
        @NotNull(message = "New quantity is required")
        @Min(value = 0, message = "New quantity must be greater than or equal to 0")
        Integer newQuantity,
        @NotNull(message = "User id is required")
        Long userId,
        @NotNull(message = "Adjustment reason is required")
        InventoryAdjustmentReason reason,
        String comment,
        LocalDate manufactureDate,
        LocalDate expirationDate
) {
}
