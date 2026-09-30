package com.isd.wms.dto.inventory;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockResponse {
    private Long id;
    private String barcode;
    private Long productId;
    private String productName;
    private Long locationId;
    private String locationBarcode;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private LocalDate manufactureDate;
    private LocalDate expirationDate;

    public String getSku() {
        return barcode;
    }

    public String getLocationCode() {
        return locationBarcode;
    }
}
