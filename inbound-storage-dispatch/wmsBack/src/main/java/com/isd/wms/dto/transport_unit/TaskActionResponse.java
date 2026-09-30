package com.isd.wms.dto.transport_unit;

public record TaskActionResponse(
    String status,
    boolean requiresScan,
    String promptHint,
    String instruction,
    String message
) {
    public TaskActionResponse(String status, boolean requiresScan, String promptHint, String message) {
        this(status, requiresScan, promptHint, null, message);
    }

    public TaskActionResponse(String status, boolean requiresScan, String message) {
        this(status, requiresScan, null, null, message);
    }
}
