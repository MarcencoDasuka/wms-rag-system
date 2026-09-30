package com.isd.wms.dto.dashboard;

import java.time.LocalDateTime;

public record ActivityFeedResponse(
        String type,
        String message,
        String actorName,
        LocalDateTime createdAt
) {
}
