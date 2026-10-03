package com.isd.wms.exception;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleOptimisticLocking_withObjectOptimisticLockingFailureException_returnsConflict() {
        ObjectOptimisticLockingFailureException ex =
            new ObjectOptimisticLockingFailureException("Stock", 1L);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleOptimisticLocking(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).contains("Concurrent modification conflict");
    }

    @Test
    void handleOptimisticLocking_withOptimisticLockException_returnsConflict() {
        OptimisticLockException ex = new OptimisticLockException("Row was updated by another transaction");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleOptimisticLocking(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
    }

    @Test
    void handleDataIntegrityViolation_withLogicIdUniqueConstraint_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "ERROR: duplicate key value violates unique constraint \"uk_orders_logic_id_lower\"\n  Detail: Key (lower(logic_id::text))=(ord-001) already exists."
        );

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).contains("logic_id already exists");
    }

    @Test
    void handleDataIntegrityViolation_withGenericUniqueConstraint_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "ERROR: duplicate key value violates unique constraint \"uk_stocks_active_location\""
        );

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("duplicate record already exists");
    }
}
