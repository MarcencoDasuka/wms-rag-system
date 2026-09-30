package com.isd.wms.dto.replenishment.shortage;

import java.util.List;

public record ShortageReplenishmentDetailsResponse(
        Long replenishmentId,
        Long taskId,
        Long destinationLocationId,
        String destinationLocationBarcode,
        String status,
        List<AffectedReplenishmentLineResponse> lines
) {
}
