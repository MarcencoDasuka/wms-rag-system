package com.isd.wms.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.isd.wms.enums.InventoryAdjustmentReason;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdjustStockRequest {
    @NotNull(message = "Stock id is required")
    private Long stockId;

    @NotNull(message = "New quantity is required")
    @Min(value = 0, message = "New quantity must be greater than or equal to 0")
    private Integer newQuantity;

    @NotNull(message = "User id is required")
    private Long userId;

    @NotNull(message = "Adjustment reason is required")
    private InventoryAdjustmentReason reason;

    private String comment;

    private LocalDate manufactureDate;
    private LocalDate expirationDate;
}
