package com.isd.wms.dto.order.shortage;

import java.util.List;

public record ShortageDetailsResponse(
        Long orderId,
        String orderNumber,
        Long destinationLocationId,
        String destinationLocationBarcode,
        String status,
        List<AffectedOrderLineResponse> lines
) {
}
