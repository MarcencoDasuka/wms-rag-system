package com.isd.wms.dto.location;

import com.isd.wms.enums.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LocationUpdateRequest (
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Barcode is required") String barcode,
        Zone zone,
        String description,
        @NotNull(message = "Availability status is required") Boolean available
) {}
