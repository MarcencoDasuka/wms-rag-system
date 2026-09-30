package com.isd.wms.exception;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
