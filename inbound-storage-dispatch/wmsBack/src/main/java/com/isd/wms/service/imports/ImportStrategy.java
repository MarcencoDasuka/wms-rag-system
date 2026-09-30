package com.isd.wms.service.imports;

import com.isd.wms.enums.ImportType;
import com.isd.wms.service.imports.csv.CsvImportStrategy;
import com.isd.wms.service.imports.xlsx.XlsxImportStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Strategy interface for importing data from files.
 * <p>
 * Implementations handle specific file formats (e.g., CSV, Excel) and convert
 * the file content into a list of typed objects based on annotations.
 * Each implementation supports a specific {@link ImportType}.
 * </p>
 *
 * @see CsvImportStrategy
 * @see XlsxImportStrategy
 */
@Component
public interface ImportStrategy {
    /**
     * Parses a file and maps its content to a list of objects of the given class.
     *
     * @param file  the file to parse
     * @param clazz the target class, usually annotated with binding annotations
     * @param <T>   the target type
     * @return a list of parsed objects
     */
    <T> List<T> parse(MultipartFile file, Class<T> clazz);

    /**
     * Checks whether this strategy supports the given import type.
     *
     * @param type the import type
     * @return true if supported
     */
    boolean support(ImportType type);
}
