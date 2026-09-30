package com.isd.wms.dto.dashboard;

public record ActiveLocationResponse(
        Long locationId,
        String locationCode,
        long movementsToday
) {
}
