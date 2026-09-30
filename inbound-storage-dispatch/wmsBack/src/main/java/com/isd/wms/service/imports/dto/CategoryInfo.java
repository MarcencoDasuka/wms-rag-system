package com.isd.wms.service.imports.dto;

import com.opencsv.bean.CsvBindByName;
import com.poiji.annotation.ExcelCellName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Import DTO for category data.
 * <p>
 * Represents a single category row from an import file. The field is bound
 * to the column "Category" in both CSV (OpenCSV) and Excel (Poiji).
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryInfo {
    @ExcelCellName(value = "Category", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Category", required = true)
    private String categoryName;
}
