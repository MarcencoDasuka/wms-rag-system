package com.isd.wms.validation;

import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentUpdateRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.ReplenishmentMapper;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.service.ReplenishmentService;
import com.isd.wms.service.validation.SecurityFacade;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def19ReplenishmentQuantityValidationRemediationTest {

    private static Validator validator;
    private ReplenishmentService replenishmentService;

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        Replenishment existing = new Replenishment();
        ReflectionTestUtils.setField(existing, "id", 100L);
        existing.setStatus(Status.CREATED);
        existing.setCreatedBy("supervisor1");
        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", 1L);
        existing.setProduct(p);
        Location loc = new Location();
        ReflectionTestUtils.setField(loc, "id", 2L);
        existing.setDestinationLocation(loc);

        ReplenishmentRepository repo = createProxy(ReplenishmentRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.of(existing);
            }
            return null;
        });

        SecurityFacade sec = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role role) {
                return true;
            }

            @Override
            public String getCurrentUsername() {
                return "supervisor1";
            }
        };

        replenishmentService = new ReplenishmentService(
            repo,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            sec
        );
    }

    @Test
    @DisplayName("ReplenishmentCreateRequest: null requestedQuantity violates @NotNull constraint")
    void createRequest_nullQuantity_violatesConstraint() {
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(1L, null, 2L);
        Set<ConstraintViolation<ReplenishmentCreateRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("requestedQuantity")
            && v.getMessage().contains("Requested quantity is required"));
    }

    @Test
    @DisplayName("ReplenishmentCreateRequest: 0 requestedQuantity violates @Min(1) constraint")
    void createRequest_zeroQuantity_violatesConstraint() {
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(1L, 0, 2L);
        Set<ConstraintViolation<ReplenishmentCreateRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("requestedQuantity")
            && v.getMessage().contains("must be at least 1"));
    }

    @Test
    @DisplayName("ReplenishmentCreateRequest: negative requestedQuantity violates @Min(1) constraint")
    void createRequest_negativeQuantity_violatesConstraint() {
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(1L, -5, 2L);
        Set<ConstraintViolation<ReplenishmentCreateRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("requestedQuantity")
            && v.getMessage().contains("must be at least 1"));
    }

    @Test
    @DisplayName("ReplenishmentCreateRequest: positive requestedQuantity passes validation")
    void createRequest_positiveQuantity_passesValidation() {
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(1L, 10, 2L);
        Set<ConstraintViolation<ReplenishmentCreateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("ReplenishmentUpdateRequest: null requestedQuantity violates @NotNull constraint")
    void updateRequest_nullQuantity_violatesConstraint() {
        ReplenishmentUpdateRequest request = new ReplenishmentUpdateRequest(1L, 1L, null, Status.CREATED, 2L);
        Set<ConstraintViolation<ReplenishmentUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("requestedQuantity")
            && v.getMessage().contains("Requested quantity is required"));
    }

    @Test
    @DisplayName("ReplenishmentUpdateRequest: 0 requestedQuantity violates @Min(1) constraint")
    void updateRequest_zeroQuantity_violatesConstraint() {
        ReplenishmentUpdateRequest request = new ReplenishmentUpdateRequest(1L, 1L, 0, Status.CREATED, 2L);
        Set<ConstraintViolation<ReplenishmentUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("requestedQuantity")
            && v.getMessage().contains("must be at least 1"));
    }

    @Test
    @DisplayName("ReplenishmentUpdateRequest: negative requestedQuantity violates @Min(1) constraint")
    void updateRequest_negativeQuantity_violatesConstraint() {
        ReplenishmentUpdateRequest request = new ReplenishmentUpdateRequest(1L, 1L, -20, Status.CREATED, 2L);
        Set<ConstraintViolation<ReplenishmentUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("requestedQuantity")
            && v.getMessage().contains("must be at least 1"));
    }

    @Test
    @DisplayName("ReplenishmentUpdateRequest: positive requestedQuantity passes validation")
    void updateRequest_positiveQuantity_passesValidation() {
        ReplenishmentUpdateRequest request = new ReplenishmentUpdateRequest(1L, 1L, 5, Status.CREATED, 2L);
        Set<ConstraintViolation<ReplenishmentUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("ReplenishmentService: createReplenishment throws InvalidRequestException when requestedQuantity <= 0")
    void service_createReplenishment_zeroOrNegative_throwsInvalidRequestException() {
        ReplenishmentCreateRequest zeroReq = new ReplenishmentCreateRequest(1L, 0, 2L);
        assertThatThrownBy(() -> replenishmentService.createReplenishment(zeroReq))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Requested quantity must be at least 1");

        ReplenishmentCreateRequest nullReq = new ReplenishmentCreateRequest(1L, null, 2L);
        assertThatThrownBy(() -> replenishmentService.createReplenishment(nullReq))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Requested quantity must be at least 1");
    }

    @Test
    @DisplayName("ReplenishmentService: updateReplenishment throws InvalidRequestException when requestedQuantity <= 0")
    void service_updateReplenishment_zeroOrNegative_throwsInvalidRequestException() {
        ReplenishmentUpdateRequest zeroReq = new ReplenishmentUpdateRequest(1L, 1L, 0, Status.CREATED, 2L);
        assertThatThrownBy(() -> replenishmentService.updateReplenishment(100L, zeroReq))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Requested quantity must be at least 1");

        ReplenishmentUpdateRequest nullReq = new ReplenishmentUpdateRequest(1L, 1L, null, Status.CREATED, 2L);
        assertThatThrownBy(() -> replenishmentService.updateReplenishment(100L, nullReq))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Requested quantity must be at least 1");
    }
}
