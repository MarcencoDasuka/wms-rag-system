package com.isd.wms.security;

import com.isd.wms.controller.ReplenishmentController;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.dto.replenishment.ReplenishmentSearchRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.entity.Task;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.mapper.ReplenishmentMapper;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.repository.TransportUnitRepository;
import com.isd.wms.service.ReplenishmentService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def17ReplenishmentSecurityRemediationTest {

    private ReplenishmentRepository replenishmentRepository;
    private ReplenishmentService replenishmentService;

    private String currentUsername;
    private Role currentRole;
    private AtomicBoolean accessibleBySupervisorCalled;
    private AtomicBoolean findAllCalled;

    private Replenishment aliceReplenishment;
    private Replenishment bobReplenishment;

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        currentUsername = "supervisor_alice";
        currentRole = Role.ROLE_SUPERVISOR;
        accessibleBySupervisorCalled = new AtomicBoolean(false);
        findAllCalled = new AtomicBoolean(false);

        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", 10L);
        p.setName("Widget A");

        Location loc = new Location();
        ReflectionTestUtils.setField(loc, "id", 20L);
        loc.setBarcode("PICK-01");

        aliceReplenishment = new Replenishment(p, 50, loc, "supervisor_alice");
        ReflectionTestUtils.setField(aliceReplenishment, "id", 101L);
        aliceReplenishment.setLogicId("REP-ALICE");
        aliceReplenishment.setStatus(Status.CREATED);

        bobReplenishment = new Replenishment(p, 30, loc, "supervisor_bob");
        ReflectionTestUtils.setField(bobReplenishment, "id", 102L);
        bobReplenishment.setLogicId("REP-BOB");
        bobReplenishment.setStatus(Status.CREATED);

        replenishmentRepository = createProxy(ReplenishmentRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findAllAccessibleBySupervisor".equals(name)) {
                accessibleBySupervisorCalled.set(true);
                return List.of(aliceReplenishment);
            }
            if ("findAll".equals(name)) {
                findAllCalled.set(true);
                return List.of(aliceReplenishment, bobReplenishment);
            }
            if ("filter".equals(name)) {
                return List.of(aliceReplenishment, bobReplenishment);
            }
            return null;
        });

        TransportUnitRepository tuRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> {
            if ("findAllByReplenishment".equals(method.getName())) {
                return Collections.emptyList();
            }
            return Collections.emptyList();
        });

        ReplenishmentMapper replenishmentMapper = new ReplenishmentMapper(tuRepository);

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public String getCurrentUsername() {
                return currentUsername;
            }

            @Override
            public boolean hasRole(Role role) {
                return currentRole == role;
            }
        };

        replenishmentService = new ReplenishmentService(
            replenishmentRepository,
            null, // stockRepository
            null, // productRepository
            null, // locationRepository
            null, // allocationRepository
            tuRepository, // transportUnitRepository
            replenishmentMapper, // replenishmentMapper
            null, // workflowService
            null, // taskService
            null, // importService
            securityFacade // securityFacade
        );
    }

    @Test
    @DisplayName("ReplenishmentController: all read and search endpoints are strictly protected by @PreAuthorize")
    void controllerEndpoints_havePreAuthorizeAnnotations() throws Exception {
        Method getAll = ReplenishmentController.class.getMethod("getAllReplenishments");
        assertThat(getAll.isAnnotationPresent(PreAuthorize.class)).isTrue();
        assertThat(getAll.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAnyRole('SUPERVISOR', 'DEV')");

        Method getById = ReplenishmentController.class.getMethod("getReplenishmentById", Long.class);
        assertThat(getById.isAnnotationPresent(PreAuthorize.class)).isTrue();
        assertThat(getById.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAnyRole('SUPERVISOR', 'DEV')");

        Method filter = ReplenishmentController.class.getMethod("searchReplenishments", ReplenishmentSearchRequest.class);
        assertThat(filter.isAnnotationPresent(PreAuthorize.class)).isTrue();
        assertThat(filter.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAnyRole('SUPERVISOR', 'DEV')");

        Method search = ReplenishmentController.class.getMethod("searchReplenishmentsFromBody", ReplenishmentSearchRequest.class);
        assertThat(search.isAnnotationPresent(PreAuthorize.class)).isTrue();
        assertThat(search.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAnyRole('SUPERVISOR', 'DEV')");
    }

    @Test
    @DisplayName("Service defense-in-depth: OPERATOR role calling getAllReplenishments throws AccessDeniedException")
    void operator_callingGetAllReplenishments_denied() {
        currentUsername = "operator_dave";
        currentRole = Role.ROLE_OPERATOR;

        assertThatThrownBy(() -> replenishmentService.getAllReplenishments())
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("only SUPERVISOR or DEV can view replenishments");

        assertThat(findAllCalled.get()).isFalse();
        assertThat(accessibleBySupervisorCalled.get()).isFalse();
    }

    @Test
    @DisplayName("Service defense-in-depth: OPERATOR role calling searchReplenishments throws AccessDeniedException")
    void operator_callingSearchReplenishments_denied() {
        currentUsername = "operator_dave";
        currentRole = Role.ROLE_OPERATOR;

        ReplenishmentSearchRequest request = new ReplenishmentSearchRequest(null, null, null, null, null, null);

        assertThatThrownBy(() -> replenishmentService.searchReplenishments(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("only SUPERVISOR or DEV can search replenishments");
    }

    @Test
    @DisplayName("Service defense-in-depth: SUPERVISOR calling getAllReplenishments uses accessible-by-supervisor scoping")
    void supervisor_callingGetAllReplenishments_scopedToOwnData() {
        currentUsername = "supervisor_alice";
        currentRole = Role.ROLE_SUPERVISOR;

        List<ReplenishmentResponse> result = replenishmentService.getAllReplenishments();

        assertThat(accessibleBySupervisorCalled.get()).isTrue();
        assertThat(findAllCalled.get()).isFalse();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Service defense-in-depth: SUPERVISOR calling searchReplenishments isolates own replenishments only")
    void supervisor_callingSearchReplenishments_filtersOutOtherSupervisors() {
        currentUsername = "supervisor_alice";
        currentRole = Role.ROLE_SUPERVISOR;

        ReplenishmentSearchRequest request = new ReplenishmentSearchRequest(null, null, null, null, null, null);
        List<ReplenishmentResponse> result = replenishmentService.searchReplenishments(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Service defense-in-depth: DEV calling getAllReplenishments and searchReplenishments has global access")
    void dev_hasGlobalAccess() {
        currentUsername = "dev_admin";
        currentRole = Role.ROLE_DEV;

        List<ReplenishmentResponse> all = replenishmentService.getAllReplenishments();
        assertThat(findAllCalled.get()).isTrue();
        assertThat(all).hasSize(2);

        ReplenishmentSearchRequest request = new ReplenishmentSearchRequest(null, null, null, null, null, null);
        List<ReplenishmentResponse> search = replenishmentService.searchReplenishments(request);
        assertThat(search).hasSize(2);
    }
}
