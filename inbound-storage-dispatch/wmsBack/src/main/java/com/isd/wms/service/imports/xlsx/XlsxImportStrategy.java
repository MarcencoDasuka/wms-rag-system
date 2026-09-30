package com.isd.wms.service.imports.xlsx;

import com.isd.wms.enums.ImportType;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.service.imports.ImportStrategy;
import com.poiji.bind.Poiji;
import com.poiji.exception.PoijiExcelType;
import com.poiji.exception.PoijiException;
import com.poiji.exception.PoijiMultiRowException;
import com.poiji.option.PoijiOptions;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Import strategy for parsing Excel files (both .xls and .xlsx) using Poiji.
 * <p>
 * Reads an Excel file from a {@link MultipartFile} and maps each row to an
 * object of the specified class using annotations ({@link com.poiji.annotation.ExcelCellName}).
 * The strategy automatically detects the Excel version from the file extension.
 * </p>
 *
 * @see ImportStrategy
 * @see ImportType#EXCEL
 */
@Component
public class XlsxImportStrategy implements ImportStrategy {

    /**
     * Parses the given Excel file and returns a list of typed objects.
     *
     * @param file  the uploaded Excel file
     * @param clazz the target class (annotated with Poiji annotations)
     * @param <T>   the target type
     * @return a list of parsed objects
     * @throws InvalidRequestException if parsing fails due to format issues or invalid data
     */
    @Override
    public <T> List<T> parse(MultipartFile file, Class<T> clazz) {
        try (InputStream inputStream = file.getInputStream()) {

            PoijiOptions options = PoijiOptions.PoijiOptionsBuilder.settings()
                .preferNullOverDefault(true)
                .datePattern("dd.MM.yyyy")
                .dateTimeFormatter(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                .build();

            PoijiExcelType excelType = PoijiExcelType.XLSX;
            if (Objects.equals(FilenameUtils.getExtension(file.getOriginalFilename()), "xls")) {
                excelType = PoijiExcelType.XLS;
            }

            return Poiji.fromExcel(inputStream, excelType, clazz, options);

        } catch (PoijiMultiRowException.PoijiRowSpecificException e) {
            throw new InvalidRequestException("Excel format is wrong in the column " +
                e.getColumnName() + " on line " + e.getRowNum());
        } catch (PoijiException e) {
            throw new InvalidRequestException(e.getLocalizedMessage());
        } catch (Exception e) {
            throw new InvalidRequestException("An error occurred while parsing the file.");
        }
    }

    @Override
    public boolean support(ImportType type) {
        return type == ImportType.EXCEL;
    }
}
