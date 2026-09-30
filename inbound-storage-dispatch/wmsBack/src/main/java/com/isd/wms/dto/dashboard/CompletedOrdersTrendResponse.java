package com.isd.wms.dto.dashboard;

import java.time.LocalDate;

public record CompletedOrdersTrendResponse(
        LocalDate date,
        long count
) {
}
