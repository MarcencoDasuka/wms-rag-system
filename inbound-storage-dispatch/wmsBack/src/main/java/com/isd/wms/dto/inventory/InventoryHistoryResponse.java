package com.isd.wms.dto.inventory;

import java.time.Instant;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryHistoryResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String barcode;
    private Integer alteredQuantity;
    private Integer quantityAfterChange;
    private Long sourceLocationId;
    private String sourceBarcode;
    private Long destinationLocationId;
    private String destinationBarcode;
    private String operationType;
    private LocalDateTime timestamp;
    private Long userId;
    private String username;
}
