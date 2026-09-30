package com.isd.wms.service.imports.dto;

import com.opencsv.bean.CsvBindByName;
import com.poiji.annotation.ExcelCellName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Import DTO for product data.
 * <p>
 * Includes product name, barcode, description, and category name.
 * The category name is used to look up or create the associated category
 * during import.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfo {
    @ExcelCellName(value = "Name", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Name", required = true)
    private String name;

    @ExcelCellName(value = "Barcode", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Barcode", required = true)
    private String barcode;

    @ExcelCellName(value = "Description", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Description", required = true)
    private String description;

    @ExcelCellName(value = "Category", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Category", required = true)
    private String categoryName;
}
