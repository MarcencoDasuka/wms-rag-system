package com.isd.wms.service.imports.dto;

import com.isd.wms.enums.Zone;
import com.opencsv.bean.CsvBindByName;
import com.poiji.annotation.ExcelCellName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Import DTO for location data.
 * <p>
 * Contains fields for location name, barcode, description, and zone.
 * All fields are mandatory.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LocationInfo {
    @ExcelCellName(value = "Name", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Name", required = true)
    private String name;

    @ExcelCellName(value = "Barcode", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Barcode", required = true)
    private String barcode;

    @ExcelCellName(value = "Description", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Description", required = true)
    private String description;

    @ExcelCellName(value = "Zone", mandatoryCell = true, mandatoryHeader = true)
    @CsvBindByName(column = "Zone", required = true)
    private Zone zone;
}
