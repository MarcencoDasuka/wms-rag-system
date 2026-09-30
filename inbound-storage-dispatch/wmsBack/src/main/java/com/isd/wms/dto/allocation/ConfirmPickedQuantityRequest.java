package com.isd.wms.dto.allocation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ConfirmPickedQuantityRequest(
        @NotNull @Min(0) Integer pickedQuantity,
        String shortageReason,
        String comment
) {
    public ConfirmPickedQuantityRequest(Integer pickedQuantity) {
        this(pickedQuantity, null, null);
    }
}
