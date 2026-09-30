package com.isd.wms.dto.location;

import com.isd.wms.enums.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LocationCreateRequest(
    @NotBlank(message = "Name is required")
    @NotNull(message = "Name is required")
    String name,
    @NotBlank(message = "Barcode is required")
    @NotNull(message = "Barcode is required")
    String barcode,
    @NotNull(message = "Zone is required")
    Zone zone,
    String description
) {
}
