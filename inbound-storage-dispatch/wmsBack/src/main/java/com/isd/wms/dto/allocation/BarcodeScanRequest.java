package com.isd.wms.dto.allocation;

import jakarta.validation.constraints.NotBlank;

public record BarcodeScanRequest(
        @NotBlank String barcode
) {
}
