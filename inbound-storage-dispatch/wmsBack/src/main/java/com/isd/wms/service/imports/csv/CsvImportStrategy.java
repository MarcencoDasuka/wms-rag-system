package com.isd.wms.service.imports.csv;

import com.isd.wms.enums.ImportType;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.service.imports.ImportStrategy;
import com.opencsv.bean.CsvToBeanBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Import strategy for parsing CSV files using OpenCSV.
 * <p>
 * This strategy reads a {@link MultipartFile} containing CSV data and converts
 * each row into an object of the specified class using annotation‑based binding
 * ({@link com.opencsv.bean.CsvBindByName}). It expects UTF‑8 encoding and
 * ignores leading whitespace.
 * </p>
 *
 * @see ImportStrategy
 * @see ImportType#CSV
 */
@Component
public class CsvImportStrategy implements ImportStrategy {

    /**
     * Parses the given CSV file and returns a list of typed objects.
     *
     * @param file  the uploaded CSV file
     * @param clazz the target class (annotated with {@code @CsvBindByName})
     * @param <T>   the target type
     * @return a list of parsed objects
     * @throws InvalidRequestException if parsing fails
     */
    @Override
    public <T> List<T> parse(MultipartFile file, Class<T> clazz) {
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {

            return new CsvToBeanBuilder<T>(reader)
                .withType(clazz)
                .withIgnoreLeadingWhiteSpace(true)
                .build()
                .parse();

        } catch (Exception e) {
            throw new InvalidRequestException("An error occurred at parsing .csv file.");
        }
    }

    /**
     * Indicates support for CSV import type.
     *
     * @param type the import type to check
     * @return true if type is {@link ImportType#CSV}
     */
    @Override
    public boolean support(ImportType type) {
        return type == ImportType.CSV;
    }
}
