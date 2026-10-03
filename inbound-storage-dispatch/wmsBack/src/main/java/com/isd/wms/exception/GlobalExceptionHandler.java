package com.isd.wms.exception;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
        CategoryNotFoundException.class,
        LocationNotFoundException.class,
        OrderLineNotFoundException.class,
        OrderNotFoundException.class,
        AllocationsNotFoundException.class,
        ProductNotFoundException.class,
        ReplenishmentNotFoundException.class,
        StockNotFoundException.class,
        TaskNotFoundException.class,
        TransportUnitNotFoundException.class,
        UserNotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler({
        DuplicateCategoryNameException.class,
        DuplicateLocationCodeException.class,
        DuplicateLocationNameException.class,
        CategoryInUseException.class,
        DuplicateBarcodeException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException exception) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler({
        ObjectOptimisticLockingFailureException.class,
        OptimisticLockException.class
    })
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(Exception exception) {
        return buildResponse(HttpStatus.CONFLICT, "Concurrent modification conflict. The resource was modified by another transaction, please retry.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        String msg = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();

        if (msg != null && (msg.toLowerCase().contains("logic_id")
                || msg.contains("uk_orders_logic_id")
                || msg.contains("uk_replenishments_logic_id"))) {
            return buildResponse(HttpStatus.CONFLICT, "A resource with the specified logic_id already exists.", Map.of());
        }

        if (msg != null && (msg.toLowerCase().contains("unique constraint")
                || msg.toLowerCase().contains("duplicate key")
                || msg.toLowerCase().contains("unique index"))) {
            return buildResponse(HttpStatus.CONFLICT, "Database constraint violation: duplicate record already exists.", Map.of());
        }

        return buildResponse(HttpStatus.CONFLICT, "Data integrity violation: the operation conflicts with current database constraints.", Map.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidRequestException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientStock(InsufficientStockException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            validationErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", validationErrors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", Map.of());
    }

    @ExceptionHandler(UserNotVerifiedException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotVerified(UserNotVerifiedException exception) {
        return buildResponse(HttpStatus.FORBIDDEN, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(DisabledException exception) {
        return buildResponse(HttpStatus.FORBIDDEN, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
        HttpStatus status,
        String message,
        Map<String, String> validationErrors
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            validationErrors
        );
        return ResponseEntity.status(status).body(response);
    }
}
