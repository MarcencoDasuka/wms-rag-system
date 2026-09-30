package com.isd.wms.service.imports;

import com.isd.wms.enums.ImportType;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.service.imports.mapper.ImportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Orchestrates the import of data from uploaded files.
 * <p>
 * This service determines the file type (CSV or Excel) from the file extension,
 * selects the appropriate {@link ImportStrategy}, parses the file into a list
 * of DTOs, and then uses a matching {@link ImportMapper} to convert those DTOs
 * into entity or request objects that can be processed by the respective
 * business services.
 * </p>
 * <p>
 * The service relies on Spring's dependency injection to collect all getAvailableQuantity
 * strategies and mappers.
 * </p>
 *
 * @see ImportStrategy
 * @see ImportMapper
 * @see ImportType
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final List<ImportStrategy> importStrategies;
    private final List<ImportMapper<?, ?>> mappers;

    @SuppressWarnings("unchecked")
    private <T, E> ImportMapper<T, E> resolveMapper(Class<T> clazz) {
        return (ImportMapper<T, E>) mappers.stream()
            .filter(m -> m.supports().equals(clazz))
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException("No mapper found for class: " + clazz.getName())
            );
    }

    /**
     * Imports data from an uploaded file.
     * <p>
     * The file type is inferred from the extension. The file is parsed into DTOs
     * using the appropriate strategy, then each DTO is mapped to a business object
     * (entity or request DTO) using a mapper that supports the DTO class.
     * </p>
     *
     * @param file  the uploaded file
     * @param clazz the DTO class that represents the file's data structure
     * @param <T>   the DTO type
     * @param <E>   the resulting entity/request type
     * @return a list of converted business objects
     * @throws InvalidRequestException if the file type is unsupported, the file name is missing,
     *                                 or parsing/mapping fails
     */
    public <T, E> List<E> importData(MultipartFile file, Class<T> clazz) {

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new InvalidRequestException("File name is missing");
        }

        String extension = FilenameUtils.getExtension(filename).toLowerCase();

        ImportType type = switch (extension) {
            case "xls", "xlsx" -> ImportType.EXCEL;
            case "csv" -> ImportType.CSV;
            default -> throw new InvalidRequestException("Unsupported file type: " + extension);
        };

        ImportStrategy strategy = importStrategies.stream()
            .filter(s -> s.support(type))
            .findFirst()
            .orElseThrow(() ->
                new InvalidRequestException("No import strategy for type: " + type)
            );

        List<T> dtos = strategy.parse(file, clazz);

        ImportMapper<T, E> mapper = resolveMapper(clazz);

        return dtos.stream()
            .map(mapper::toEntity)
            .toList();
    }
}
