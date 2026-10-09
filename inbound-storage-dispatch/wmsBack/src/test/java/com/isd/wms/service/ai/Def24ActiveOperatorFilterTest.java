package com.isd.wms.service.ai;

import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.repository.*;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.ReplenishmentService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class Def24ActiveOperatorFilterTest {

    private WarehouseAiTools warehouseAiTools;
    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private ReplenishmentRepository replenishmentRepository;
    private OrderService orderService;
    private ReplenishmentService replenishmentService;
    private AiToolSecurityBoundary securityBoundary;

    private User activeOperator1;
    private User activeOperator2;
    private User inactiveOperator;

    private final AtomicBoolean queriedOnlyActive = new AtomicBoolean(false);

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "supervisor_alice",
            "pass",
            List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        activeOperator1 = new User("active_bob", "bob@isd.com", "pass", Role.ROLE_OPERATOR, true, null, null);
        ReflectionTestUtils.setField(activeOperator1, "id", 10L);

        activeOperator2 = new User("active_charlie", "charlie@isd.com", "pass", Role.ROLE_OPERATOR, true, null, null);
        ReflectionTestUtils.setField(activeOperator2, "id", 20L);

        inactiveOperator = new User("inactive_dan", "dan@isd.com", "pass", Role.ROLE_OPERATOR, false, null, null);
        ReflectionTestUtils.setField(inactiveOperator, "id", 30L);

        userRepository = createProxy(UserRepository.class, (proxy, method, args) -> {
            if ("findByUserRoleAndIsActiveTrue".equals(method.getName())) {
                queriedOnlyActive.set(true);
                Role role = (Role) args[0];
                if (role == Role.ROLE_OPERATOR) {
                    return List.of(activeOperator1, activeOperator2);
                }
                return Collections.emptyList();
            }
            if ("findByUserRole".equals(method.getName())) {
                return List.of(activeOperator1, activeOperator2, inactiveOperator);
            }
            return null;
        });

        orderRepository = createProxy(OrderRepository.class, (proxy, method, args) -> {
            if ("findAll".equals(method.getName())) {
                Order order = new Order("ORD-001");
                ReflectionTestUtils.setField(order, "id", 101L);
                order.setStatus(OrderStatus.CREATED);
                return List.of(order);
            }
            return Collections.emptyList();
        });

        replenishmentRepository = createProxy(ReplenishmentRepository.class, (proxy, method, args) -> Collections.emptyList());
        orderService = new OrderService(null, null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public void assignOrder(Long orderId, Long operatorId) {
                // simulated successful assignment in test
            }
        };
        replenishmentService = new ReplenishmentService(null, null, null, null, null, null, null, null, null, null, null);

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public String getCurrentUsername() {
                return "supervisor_alice";
            }
        };

        securityBoundary = new AiToolSecurityBoundary(
            securityFacade,
            orderRepository
        );

        warehouseAiTools = new WarehouseAiTools(
            createProxy(LocationRepository.class, (p, m, a) -> null),
            userRepository,
            orderRepository,
            replenishmentRepository,
            orderService,
            replenishmentService,
            securityBoundary
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("DEF-24: getAvailableOperators strictly queries active operators and excludes inactive operators")
    void getAvailableOperators_ExcludesInactiveOperators() {
        String result = warehouseAiTools.getAvailableOperators();

        assertThat(queriedOnlyActive.get()).isTrue();
        assertThat(result).contains("active_bob");
        assertThat(result).contains("active_charlie");
        assertThat(result).doesNotContain("inactive_dan");
    }

    @Test
    @DisplayName("DEF-24: autoDistributeWorkload distributes work strictly to active operators")
    void autoDistributeWorkload_UsesOnlyActiveOperators() {
        String result = warehouseAiTools.autoDistributeWorkload();

        assertThat(queriedOnlyActive.get()).isTrue();
        assertThat(result).contains("Assigned 1 Orders and 0 Replenishments among 2 operators");
    }

    @Test
    @DisplayName("DEF-24: When no active operators exist, getAvailableOperators returns clear message")
    void getAvailableOperators_NoActiveOperators_ReturnsMessage() {
        UserRepository emptyActiveRepo = createProxy(UserRepository.class, (proxy, method, args) -> {
            if ("findByUserRoleAndIsActiveTrue".equals(method.getName())) {
                return Collections.emptyList();
            }
            return Collections.emptyList();
        });

        WarehouseAiTools tools = new WarehouseAiTools(
            createProxy(LocationRepository.class, (p, m, a) -> null),
            emptyActiveRepo,
            orderRepository,
            replenishmentRepository,
            orderService,
            replenishmentService,
            securityBoundary
        );

        String result = tools.getAvailableOperators();
        assertThat(result).isEqualTo("No operators found in the system.");
    }
}
