package com.isd.wms.service;

import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.enums.Role;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.ReplenishmentMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def11ReplenishmentDestinationConflictRemediationTest {

    private ReplenishmentRepository replenishmentRepository;
    private ProductRepository productRepository;
    private LocationRepository locationRepository;

    private ReplenishmentService replenishmentService;

    private Product productA;
    private Product productB;
    private Location destinationLocation;

    private boolean existsByDestinationLocationActive;
    private boolean replenishmentSaved;

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        existsByDestinationLocationActive = false;
        replenishmentSaved = false;

        productA = new Product();
        ReflectionTestUtils.setField(productA, "id", 101L);
        productA.setName("Product Alpha");
        productA.setAutoReplenish(true);
        productA.setMinThreshold(10);
        productA.setReplenishQty(20);

        productB = new Product();
        ReflectionTestUtils.setField(productB, "id", 102L);
        productB.setName("Product Beta");

        destinationLocation = new Location();
        ReflectionTestUtils.setField(destinationLocation, "id", 50L);
        destinationLocation.setName("PICK-LOC-01");
        destinationLocation.setBarcode("LOC-PICK-01");
        destinationLocation.setAvailable(true);

        replenishmentRepository = createProxy(ReplenishmentRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("existsByDestinationLocationIdAndStatusIn".equals(name)) {
                Long locId = (Long) args[0];
                if (Long.valueOf(50L).equals(locId)) {
                    return existsByDestinationLocationActive;
                }
                return false;
            }
            if ("existsByLogicIdIgnoreCase".equals(name)) {
                return false;
            }
            if ("save".equals(name)) {
                replenishmentSaved = true;
                Replenishment r = (Replenishment) args[0];
                ReflectionTestUtils.setField(r, "id", 999L);
                return r;
            }
            return null;
        });

        productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                Long id = (Long) args[0];
                if (Long.valueOf(101L).equals(id)) return Optional.of(productA);
                if (Long.valueOf(102L).equals(id)) return Optional.of(productB);
                return Optional.empty();
            }
            return Optional.empty();
        });

        locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                Long id = (Long) args[0];
                if (Long.valueOf(50L).equals(id)) return Optional.of(destinationLocation);
                return Optional.empty();
            }
            return Optional.empty();
        });

        TransportUnitRepository transportUnitRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> {
            if ("findFirstByReplenishmentOrderByCreatedAtAscIdAsc".equals(method.getName())) {
                return Optional.empty();
            }
            return Optional.empty();
        });

        ReplenishmentMapper replenishmentMapper = new ReplenishmentMapper(transportUnitRepository);

        StockRepository stockRepository = createProxy(StockRepository.class, (proxy, method, args) -> Optional.empty());
        AllocationRepository allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> Collections.emptyList());

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role roleName) {
                return false;
            }

            @Override
            public String getCurrentUsername() {
                return "supervisor_1";
            }
        };

        replenishmentService = new ReplenishmentService(
                replenishmentRepository,
                stockRepository,
                productRepository,
                locationRepository,
                allocationRepository,
                transportUnitRepository,
                replenishmentMapper,
                null,
                null,
                null,
                securityFacade
        );
    }

    @Test
    @DisplayName("DEF-11: createReplenishment must reject creation when destination has active replenishment, even with different product")
    void createReplenishment_whenActiveReplenishmentExistsForDifferentProduct_mustThrowInvalidRequestException() {
        // Active replenishment exists in destination location 50L (e.g. for Product A)
        existsByDestinationLocationActive = true;

        // Attacker or operator attempts to create replenishment for Product B to the same destination location 50L
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(102L, 20, 50L);

        assertThatThrownBy(() -> replenishmentService.createReplenishment(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Location already has an active replenishment in progress");
    }

    @Test
    @DisplayName("DEF-11: createReplenishment must reject creation when destination has active replenishment for same product")
    void createReplenishment_whenActiveReplenishmentExistsForSameProduct_mustThrowInvalidRequestException() {
        existsByDestinationLocationActive = true;

        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(101L, 10, 50L);

        assertThatThrownBy(() -> replenishmentService.createReplenishment(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Location already has an active replenishment in progress");
    }

    @Test
    @DisplayName("DEF-11: createReplenishment must succeed when destination has no active replenishments")
    void createReplenishment_whenNoActiveReplenishmentExists_mustSucceed() {
        // Destination location is completely free of active replenishments
        existsByDestinationLocationActive = false;

        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(101L, 15, 50L);

        ReplenishmentResponse response = replenishmentService.createReplenishment(request);
        assertThat(response).isNotNull();
        assertThat(response.destinationLocationId()).isEqualTo(50L);
        assertThat(response.productId()).isEqualTo(101L);
        assertThat(replenishmentSaved).isTrue();
    }

    @Test
    @DisplayName("DEF-11: checkAndTriggerAutoReplenishment must skip creation if destination location already has active replenishment")
    void checkAndTriggerAutoReplenishment_whenActiveReplenishmentExists_mustSkip() {
        existsByDestinationLocationActive = true;

        // When auto replenishment is checked for destination location 50L with low quantity
        replenishmentService.checkAndTriggerAutoReplenishment(productA, destinationLocation, 5);

        // Verification: auto replenishment did NOT create a new replenishment
        assertThat(replenishmentSaved).isFalse();
    }
}
