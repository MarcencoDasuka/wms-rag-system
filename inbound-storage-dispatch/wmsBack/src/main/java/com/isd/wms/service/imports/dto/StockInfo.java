package com.isd.wms.service.imports.dto;

import com.opencsv.bean.CsvBindByName;
import com.poiji.annotation.ExcelCellName;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Import DTO for stock data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockInfo {
    @ExcelCellName(value = "Product", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Product", required = true)
    @NonNull
    @NotBlank
    private String productName;

    @ExcelCellName(value = "Location", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Location", required = true)
    @NonNull
    @NotBlank
    private String locationName;

    @ExcelCellName(value = "Quantity", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Quantity", required = true)
    @NonNull
    private Integer quantity;

    @ExcelCellName(value = "Reserved Quantity", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Reserved Quantity", required = true)
    @NonNull
    private Integer reservedQuantity;

    @ExcelCellName(value = "Manufacture Date")
    @CsvBindByName(column = "Manufacture Date")
    private String manufactureDateRaw;

    @ExcelCellName(value = "Expiration Date")
    @CsvBindByName(column = "Expiration Date")
    private String expirationDateRaw;
}
