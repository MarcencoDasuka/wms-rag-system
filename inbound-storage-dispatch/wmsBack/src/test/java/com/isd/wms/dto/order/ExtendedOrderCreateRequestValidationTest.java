package com.isd.wms.dto.order;

import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExtendedOrderCreateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("PRE-FIX/POST-FIX: Validates that invalid child order lines produce violations when cascading @Valid is applied")
    void validate_invalidNestedLines_shouldProduceConstraintViolations() {
        // Construct line with invalid quantity (-5)
        OrderLineCreateRequest invalidLine = new OrderLineCreateRequest(1L, 10L, -5);
        OrderCreateRequest validOrder = new OrderCreateRequest("ORD-1", 100L);

        ExtendedOrderCreateRequest request = new ExtendedOrderCreateRequest(validOrder, List.of(invalidLine));

        Set<ConstraintViolation<ExtendedOrderCreateRequest>> violations = validator.validate(request);

        // On PRE-FIX: without @Valid on lines, violations is empty (0 errors detected!)
        // On POST-FIX: with @Valid cascade, violations detects requestedQuantity < 0
        assertThat(violations)
                .as("Cascading validation must catch invalid requestedQuantity in nested order line")
                .isNotEmpty();

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().contains("lines")
                        && v.getMessage().contains("quantity"));
    }

    @Test
    @DisplayName("Valid request with valid order and lines must have zero violations")
    void validate_validRequest_shouldHaveNoViolations() {
        OrderLineCreateRequest validLine = new OrderLineCreateRequest(1L, 10L, 5);
        OrderCreateRequest validOrder = new OrderCreateRequest("ORD-1", 100L);

        ExtendedOrderCreateRequest request = new ExtendedOrderCreateRequest(validOrder, List.of(validLine));

        Set<ConstraintViolation<ExtendedOrderCreateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
