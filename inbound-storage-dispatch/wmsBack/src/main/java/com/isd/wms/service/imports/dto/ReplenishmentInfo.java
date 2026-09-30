package com.isd.wms.service.imports.dto;

import com.opencsv.bean.CsvBindByName;
import com.poiji.annotation.ExcelCellName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Import DTO for replenishment data.
 * <p>
 * Describes a replenishment request with product name, requested quantity,
 * and destination location name. The product and location are resolved by name
 * during the import process.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReplenishmentInfo {
    @ExcelCellName(value = "Product", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Product", required = true)
    private String product;

    @ExcelCellName(value = "Requested Quantity", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Requested Quantity", required = true)
    private Integer requestedQuantity;

    @ExcelCellName(value = "Destination Location", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Destination Location", required = true)
    private String destinationLocationName;
}
