package com.isd.wms.dto.dashboard;

import java.time.LocalDateTime;

public record NeedsAttentionResponse(
        String type,
        String severity,
        String title,
        String description,
        Long relatedEntityId,
        LocalDateTime createdAt
) {
}
