package com.isd.wms.dto.replenishment.shortage;

import java.time.LocalDateTime;

public record ShortageReplenishmentResponse(
        Long replenishmentId,
        Long taskId,
        String destination,
        String status,
        Integer totalLines,
        Integer shortageLines,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
