package com.isd.wms.security;

import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.dto.replenishment.ReplenishmentUpdateRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.entity.Task;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.mapper.ReplenishmentMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.ReplenishmentService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def04ReplenishmentBolaRemediationTest {

    private ReplenishmentRepository replenishmentRepository;
    private ReplenishmentService replenishmentService;

    private String currentUsername;
    private boolean isDevUser;
    private User currentUser;

    private Replenishment aliceReplenishment;

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        currentUsername = "supervisor_alice";
        isDevUser = false;
        currentUser = new User("supervisor_alice", "alice@isd.com", "pass", Role.ROLE_SUPERVISOR, true, null, null);
        ReflectionTestUtils.setField(currentUser, "id", 101L);

        aliceReplenishment = new Replenishment();
        ReflectionTestUtils.setField(aliceReplenishment, "id", 601L);
        aliceReplenishment.setLogicId("REP-001");
        aliceReplenishment.setCreatedBy("supervisor_alice");
        aliceReplenishment.setStatus(Status.CREATED);

        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", 10L);
        p.setName("Product Alpha");
        aliceReplenishment.setProduct(p);

        Location loc = new Location();
        ReflectionTestUtils.setField(loc, "id", 20L);
        loc.setBarcode("LOC-01");
        aliceReplenishment.setDestinationLocation(loc);

        replenishmentRepository = createProxy(ReplenishmentRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findById".equals(name)) {
                Long id = (Long) args[0];
                if (Long.valueOf(601L).equals(id)) {
                    return Optional.of(aliceReplenishment);
                }
                return Optional.empty();
            }
            if ("save".equals(name)) {
                return args[0];
            }
            if ("delete".equals(name)) {
                return null;
            }
            return null;
        });

        TransportUnitRepository transportUnitRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> {
            if ("findFirstByReplenishmentOrderByCreatedAtAscIdAsc".equals(method.getName())) {
                return Optional.empty();
            }
            if ("findAllByReplenishment".equals(method.getName())) {
                return Collections.emptyList();
            }
            return Collections.emptyList();
        });

        ReplenishmentMapper replenishmentMapper = new ReplenishmentMapper(transportUnitRepository);

        StockRepository stockRepository = createProxy(StockRepository.class, (proxy, method, args) -> Optional.empty());
        ProductRepository productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> Optional.empty());
        LocationRepository locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> Optional.empty());
        AllocationRepository allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> Collections.emptyList());

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role roleName) {
                if (roleName == Role.ROLE_DEV) {
                    return isDevUser;
                }
                return false;
            }

            @Override
            public String getCurrentUsername() {
                return currentUsername;
            }

            @Override
            public User getCurrentUser() {
                return currentUser;
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
    @DisplayName("DEF-04: Attacking supervisor cannot access replenishment created by another supervisor (BOLA 403)")
    void getReplenishmentById_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";

        assertThatThrownBy(() -> replenishmentService.getReplenishmentById(601L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("DEF-04: Creator supervisor can access unassigned CREATED replenishment (task is null)")
    void getReplenishmentById_byCreatorSupervisor_mustSucceed() {
        currentUsername = "supervisor_alice";

        ReplenishmentResponse result = replenishmentService.getReplenishmentById(601L);
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(601L);
        assertThat(aliceReplenishment.getCreatedBy()).isEqualTo("supervisor_alice");
    }

    @Test
    @DisplayName("DEF-04: Task supervisor can access replenishment even if created by another user")
    void getReplenishmentById_byTaskSupervisor_mustSucceed() {
        // Replenishment created by Bob
        aliceReplenishment.setCreatedBy("supervisor_bob");

        // But Alice is task supervisor
        User supervisorAlice = new User("supervisor_alice", "alice@isd.com", "pass", Role.ROLE_SUPERVISOR, true, null, null);
        Task task = new Task();
        task.setSupervisor(supervisorAlice);
        aliceReplenishment.setTask(task);

        currentUsername = "supervisor_alice";

        ReplenishmentResponse result = replenishmentService.getReplenishmentById(601L);
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(601L);
    }

    @Test
    @DisplayName("DEF-04: ROLE_DEV can access any replenishment bypassing ownership checks")
    void getReplenishmentById_byDevUser_mustBypassOwnershipCheck() {
        currentUsername = "developer_dave";
        isDevUser = true;

        ReplenishmentResponse result = replenishmentService.getReplenishmentById(601L);
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(601L);
    }

    @Test
    @DisplayName("DEF-04: Attacking supervisor cannot cancel replenishment of another supervisor (BOLA 403)")
    void cancelReplenishment_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";

        assertThatThrownBy(() -> replenishmentService.cancelReplenishment(601L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("DEF-04: Attacking supervisor cannot delete replenishment of another supervisor (BOLA 403)")
    void deleteReplenishment_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";

        assertThatThrownBy(() -> replenishmentService.deleteReplenishment(601L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("DEF-04: Attacking supervisor cannot update replenishment of another supervisor (BOLA 403)")
    void updateReplenishment_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";
        ReplenishmentUpdateRequest request = new ReplenishmentUpdateRequest(null, 10L, 10, Status.CREATED, 20L);

        assertThatThrownBy(() -> replenishmentService.updateReplenishment(601L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("DEF-04: Attacking supervisor cannot assign replenishment of another supervisor (BOLA 403)")
    void assignReplenishment_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";

        assertThatThrownBy(() -> replenishmentService.assignReplenishment(601L, 999L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }
}
