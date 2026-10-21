package com.isd.wms.service;

import com.isd.wms.controller.AiChatController;
import com.isd.wms.dto.order.OrderFilterRequest;
import com.isd.wms.dto.order.OrderResponse;
import com.isd.wms.dto.order.shortage.ShortageOrderResponse;
import com.isd.wms.entity.*;
import com.isd.wms.enums.InventoryAdjustmentReason;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.mapper.OrderLineMapper;
import com.isd.wms.mapper.OrderMapper;
import com.isd.wms.repository.*;
import com.isd.wms.repository.projections.AllocationSupervisorProjection;
import com.isd.wms.service.ai.*;
import com.isd.wms.service.validation.SecurityFacade;
import com.isd.wms.validation.StrongPasswordValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Def27Def30SecurityAndIsolationTest {

    @Mock private SecurityFacade securityFacade;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockRepository stockRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserRepository userRepository;
    @Mock private InventoryService inventoryService;
    @Mock private InventoryAdjustmentService inventoryAdjustmentService;
    @Mock private OrderService orderService;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private ReplenishmentRepository replenishmentRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private OrderMapper orderMapper;

    private AiToolSecurityBoundary securityBoundary;
    private InventoryMutatingAiTools inventoryMutatingAiTools;
    private AiChatController aiChatController;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        securityBoundary = new AiToolSecurityBoundary(securityFacade, orderRepository);
        inventoryMutatingAiTools = new InventoryMutatingAiTools(
            productRepository,
            stockRepository,
            locationRepository,
            userRepository,
            inventoryService,
            inventoryAdjustmentService,
            securityFacade,
            securityBoundary
        );
        aiChatController = new AiChatController(
            null,
            securityBoundary,
            orderService,
            orderRepository,
            inventoryMutatingAiTools
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        securityBoundary.clearPendingConfirmations();
    }

    private void authenticateAs(String username, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            username,
            "pass",
            List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContext sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(auth);
        SecurityContextHolder.setContext(sc);
    }

    // =========================================================================
    // DEF-27: ADJUST_INVENTORY_STOCK Confirmation, Execution & 15-Minute TTL
    // =========================================================================

    @Test
    @DisplayName("DEF-27: adjustInventoryStock initiates pending operation with 15-minute TTL and structured params")
    void def27_adjustInventoryStock_initiatesPendingOperationWith15MinTtl() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_sam");

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 10L);
        product.setBarcode("PROD-BAR-10");
        product.setName("Widget 10");

        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 20L);
        location.setBarcode("LOC-BAR-20");

        Stock stock = new Stock();
        ReflectionTestUtils.setField(stock, "id", 30L);
        stock.setQuantity(50);
        stock.setProduct(product);
        stock.setLocation(location);

        when(productRepository.findByBarcode("PROD-BAR-10")).thenReturn(Optional.of(product));
        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(stockRepository.findByProductIdAndLocationIdAndAvailableIsTrue(10L, 20L)).thenReturn(Optional.of(stock));

        String prompt = inventoryMutatingAiTools.adjustInventoryStock(
            "PROD-BAR-10",
            "LOC-BAR-20",
            25,
            "DAMAGED",
            "Water damage on box"
        );

        assertThat(prompt).contains("PENDING HUMAN CONFIRMATION");
        assertThat(prompt).contains("Action 'ADJUST_INVENTORY_STOCK'");
        assertThat(prompt).contains("PROD-BAR-10@LOC-BAR-20");

        List<AiToolSecurityBoundary.PendingConfirmation> pendingOps = securityBoundary.getPendingOperations();
        assertThat(pendingOps).hasSize(1);
        AiToolSecurityBoundary.PendingConfirmation pending = pendingOps.get(0);

        assertThat(pending.actionType()).isEqualTo("ADJUST_INVENTORY_STOCK");
        assertThat(pending.targetId()).isEqualTo("PROD-BAR-10@LOC-BAR-20");
        assertThat(pending.targetEntityId()).isEqualTo(30L);
        assertThat(pending.newQuantity()).isEqualTo(25);
        assertThat(pending.reason()).isEqualTo("DAMAGED");
        assertThat(pending.username()).isEqualTo("supervisor_sam");

        Instant now = Instant.now();
        assertThat(pending.expiresAt())
            .isAfter(now.plus(14, ChronoUnit.MINUTES))
            .isBefore(now.plus(16, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("DEF-27: Human supervisor confirms ADJUST_INVENTORY_STOCK executing stock mutation")
    void def27_confirmAdjustInventoryStock_humanConfirmationExecutesStockMutation() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_sam");

        User user = new User("supervisor_sam", "sam@isd.com", "pass", Role.ROLE_SUPERVISOR, true, null, null);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByUsername("supervisor_sam")).thenReturn(Optional.of(user));

        String prompt = securityBoundary.initiatePendingOperation(
            "ADJUST_INVENTORY_STOCK",
            "PROD-10@LOC-20",
            30L,
            "Adjust quantity to 25 (reason: DAMAGED)",
            25,
            "DAMAGED"
        );

        String opId = prompt.substring(prompt.indexOf("OP-"), prompt.indexOf("'. For"));

        ResponseEntity<Map<String, Object>> response = aiChatController.confirmOperation(opId);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "SUCCESS");
        assertThat(response.getBody()).containsEntry("actionType", "ADJUST_INVENTORY_STOCK");

        verify(inventoryAdjustmentService).adjustStock(eq(30L), argThat(req ->
            req.newQuantity() == 25 &&
            req.reason() == InventoryAdjustmentReason.DAMAGED &&
            req.userId().equals(1L)
        ));

        // Replay attempt must fail
        assertThatThrownBy(() -> aiChatController.confirmOperation(opId))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Invalid or expired operation ID");
    }

    @Test
    @DisplayName("DEF-27: Autonomous AI confirmation token passing is strictly blocked")
    void def27_autonomousAiConfirmation_isStrictlyBlocked() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");

        assertThatThrownBy(() -> inventoryMutatingAiTools.adjustInventoryStock(
            "PROD-BAR-10", "LOC-BAR-20", 25, "DAMAGED", "comment", "forged-token-abc"
        )).isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Autonomous AI tool confirmation is disabled");
    }

    @Test
    @DisplayName("DEF-27: confirmOperation with unsupported action type throws IllegalStateException")
    void def27_confirmOperation_unsupportedActionType_throwsIllegalStateException() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_sam");

        String prompt = securityBoundary.initiatePendingOperation(
            "UNSUPPORTED_ACTION",
            "TARGET-1",
            100L,
            "Some unsupported action"
        );
        String opId = prompt.substring(prompt.indexOf("OP-"), prompt.indexOf("'. For"));

        assertThatThrownBy(() -> aiChatController.confirmOperation(opId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown or unsupported action type");
    }

    // =========================================================================
    // DEF-28: OrderLine BOLA & Status Guards
    // =========================================================================

    @Test
    @DisplayName("DEF-28: OrderLineService.getAll filters order lines by supervisor when non-DEV")
    void def28_getAll_filtersBySupervisorWhenNonDev() {
        OrderLineMapper orderLineMapper = new OrderLineMapper();
        OrderLineService orderLineService = new OrderLineService(
            orderRepository,
            orderLineRepository,
            orderLineMapper,
            productRepository,
            mock(TaskRepository.class),
            allocationRepository,
            stockRepository,
            securityFacade
        );

        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);
        when(securityFacade.getCurrentUserId()).thenReturn(10L);
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_sam");

        OrderLine ol1 = new OrderLine();
        ReflectionTestUtils.setField(ol1, "id", 1L);
        Order order1 = new Order("ORD-1");
        ReflectionTestUtils.setField(order1, "id", 100L);
        User sam = new User();
        sam.setId(10L);
        order1.setSupervisor(sam);
        ol1.setOrder(order1);
        ol1.setProduct(new Product());

        OrderLine ol2 = new OrderLine();
        ReflectionTestUtils.setField(ol2, "id", 2L);
        Order order2 = new Order("ORD-2");
        ReflectionTestUtils.setField(order2, "id", 200L);
        User bob = new User();
        bob.setId(20L);
        order2.setSupervisor(bob);
        ol2.setOrder(order2);
        ol2.setProduct(new Product());

        when(orderLineRepository.findAll()).thenReturn(List.of(ol1, ol2));

        var result = orderLineService.getAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderLineId()).isEqualTo(1L);
    }

    // =========================================================================
    // DEF-29: Supervisor BOLA Isolation in Search, Allocations, and AI Tools
    // =========================================================================

    @Test
    @DisplayName("DEF-29: OrderService.searchOrders restricts results to supervisor's orders when non-DEV")
    void def29_searchOrders_supervisorIsolationWhenNonDev() {
        OrderService testOrderService = new OrderService(
            mock(com.isd.wms.mapper.ExtendedOrderMapper.class),
            orderMapper,
            orderRepository,
            locationRepository,
            mock(OrderLineService.class),
            allocationRepository,
            mock(TaskRepository.class),
            orderLineRepository,
            mock(com.isd.wms.service.imports.ImportService.class),
            securityFacade,
            mock(TaskService.class),
            mock(TransportUnitRepository.class)
        );

        User supSam = new User();
        supSam.setId(10L);
        supSam.setUsername("supervisor_sam");

        User supBob = new User();
        supBob.setId(20L);
        supBob.setUsername("supervisor_bob");

        Order samOrder = new Order("ORD-SAM");
        ReflectionTestUtils.setField(samOrder, "id", 1L);
        samOrder.setSupervisor(supSam);

        Order bobOrder = new Order("ORD-BOB");
        ReflectionTestUtils.setField(bobOrder, "id", 2L);
        bobOrder.setSupervisor(supBob);

        when(orderRepository.filter(any(), any(), any(), any(), any()))
            .thenReturn(List.of(samOrder, bobOrder));
        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);
        when(securityFacade.getCurrentUserId()).thenReturn(10L);
        when(orderMapper.toResponse(eq(samOrder), any(), any())).thenReturn(
            new OrderResponse(1L, "ORD-SAM", 1L, OrderStatus.CREATED, null, null)
        );

        var list = testOrderService.searchOrders(new OrderFilterRequest(null, null, null, null, null, null));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).logicId()).isEqualTo("ORD-SAM");
    }

    @Test
    @DisplayName("DEF-29: AllocationService.getAllAllocations restricts to supervisor's orders when non-DEV")
    void def29_getAllAllocations_supervisorIsolationWhenNonDev() {
        AllocationService allocationService = new AllocationService(
            allocationRepository,
            securityFacade,
            orderLineRepository,
            replenishmentRepository
        );

        AllocationSupervisorProjection proj1 = mock(AllocationSupervisorProjection.class);
        AllocationSupervisorProjection proj2 = mock(AllocationSupervisorProjection.class);

        Order samOrder = new Order("ORD-1");
        User sam = new User();
        sam.setId(10L);
        samOrder.setSupervisor(sam);

        Order bobOrder = new Order("ORD-2");
        User bob = new User();
        bob.setId(20L);
        bobOrder.setSupervisor(bob);

        when(proj1.getOrder()).thenReturn(samOrder);
        when(proj2.getOrder()).thenReturn(bobOrder);
        when(allocationRepository.getAllAllocations()).thenReturn(List.of(proj1, proj2));

        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);
        when(securityFacade.getCurrentUserId()).thenReturn(10L);

        List<AllocationSupervisorProjection> result = allocationService.getAllAllocations();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrder().getSupervisor().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("DEF-29: AllocationService.getAllAllocations retains supervisor's own REPLENISHMENT allocation while filtering out other supervisor's allocations")
    void def29_getAllAllocations_retainsSupervisorReplenishmentAllocationWhenNonDev() {
        AllocationService allocationService = new AllocationService(
            allocationRepository,
            securityFacade,
            orderLineRepository,
            replenishmentRepository
        );

        AllocationSupervisorProjection samReplProj = mock(AllocationSupervisorProjection.class);
        AllocationSupervisorProjection bobReplProj = mock(AllocationSupervisorProjection.class);

        lenient().when(samReplProj.getOrderId()).thenReturn(null);
        lenient().when(samReplProj.getOrder()).thenReturn(null);
        when(samReplProj.getSupervisorId()).thenReturn(10L);
        lenient().when(samReplProj.getSupervisorUsername()).thenReturn("supervisor_sam");

        lenient().when(bobReplProj.getOrderId()).thenReturn(null);
        lenient().when(bobReplProj.getOrder()).thenReturn(null);
        when(bobReplProj.getSupervisorId()).thenReturn(20L);
        lenient().when(bobReplProj.getSupervisorUsername()).thenReturn("supervisor_bob");

        when(allocationRepository.getAllAllocations()).thenReturn(List.of(samReplProj, bobReplProj));
        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);
        when(securityFacade.getCurrentUserId()).thenReturn(10L);
        lenient().when(securityFacade.getCurrentUsername()).thenReturn("supervisor_sam");

        List<AllocationSupervisorProjection> result = allocationService.getAllAllocations();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSupervisorId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("DEF-29: OrderAiTools.getActiveOrdersInfo isolates orders by supervisor when non-DEV")
    void def29_orderAiTools_getActiveOrdersInfo_supervisorIsolation() {
        OrderAiTools tools = new OrderAiTools(orderService, orderRepository, null, securityFacade);

        User supSam = new User();
        supSam.setId(10L);
        User supBob = new User();
        supBob.setId(20L);

        Order samOrder = new Order("LOGIC-SAM");
        ReflectionTestUtils.setField(samOrder, "id", 1L);
        samOrder.setStatus(OrderStatus.CREATED);
        samOrder.setSupervisor(supSam);

        Order bobOrder = new Order("LOGIC-BOB");
        ReflectionTestUtils.setField(bobOrder, "id", 2L);
        bobOrder.setStatus(OrderStatus.CREATED);
        bobOrder.setSupervisor(supBob);

        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_sam");
        when(securityFacade.getCurrentUserId()).thenReturn(10L);
        when(orderRepository.findAllAccessibleBySupervisor("supervisor_sam"))
            .thenReturn(List.of(samOrder, bobOrder));

        String info = tools.getActiveOrdersInfo();

        assertThat(info).contains("LOGIC-SAM");
        assertThat(info).doesNotContain("LOGIC-BOB");
    }

    @Test
    @DisplayName("DEF-29: ReplenishmentAiTools.getActiveReplenishmentsInfo isolates replenishments by supervisor when non-DEV")
    void def29_replenishmentAiTools_getActiveReplenishmentsInfo_supervisorIsolation() {
        ReplenishmentAiTools tools = new ReplenishmentAiTools(
            null,
            replenishmentRepository,
            productRepository,
            locationRepository,
            userRepository,
            securityBoundary,
            securityFacade
        );

        User supSam = new User();
        supSam.setId(10L);
        User supBob = new User();
        supBob.setId(20L);

        Product p = new Product();
        p.setName("Test Widget");

        Replenishment samRepl = new Replenishment();
        samRepl.setLogicId("REPL-SAM");
        samRepl.setStatus(Status.CREATED);
        samRepl.setSupervisor(supSam);
        samRepl.setProduct(p);

        Replenishment bobRepl = new Replenishment();
        bobRepl.setLogicId("REPL-BOB");
        bobRepl.setStatus(Status.CREATED);
        bobRepl.setSupervisor(supBob);
        bobRepl.setProduct(p);

        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_sam");
        when(securityFacade.getCurrentUserId()).thenReturn(10L);
        when(replenishmentRepository.findByStatus(Status.IN_PROGRESS)).thenReturn(List.of());
        when(replenishmentRepository.findByStatus(Status.ASSIGNED)).thenReturn(List.of());
        when(replenishmentRepository.findByStatus(Status.CREATED)).thenReturn(List.of(samRepl, bobRepl));

        String info = tools.getActiveReplenishmentsInfo();

        assertThat(info).contains("REPL-SAM");
        assertThat(info).doesNotContain("REPL-BOB");
    }

    @Test
    @DisplayName("DEF-29: WarehouseAiTools.autoDistributeWorkload restricts candidate orders to caller supervisor")
    void def29_warehouseAiTools_autoDistributeWorkload_supervisorIsolation() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        WarehouseAiTools tools = new WarehouseAiTools(
            locationRepository,
            userRepository,
            orderRepository,
            replenishmentRepository,
            orderService,
            null,
            securityBoundary,
            securityFacade
        );

        User supSam = new User();
        supSam.setId(10L);
        User supBob = new User();
        supBob.setId(20L);

        Order samOrder = new Order("ORD-SAM");
        ReflectionTestUtils.setField(samOrder, "id", 100L);
        samOrder.setStatus(OrderStatus.CREATED);
        samOrder.setSupervisor(supSam);

        Order bobOrder = new Order("ORD-BOB");
        ReflectionTestUtils.setField(bobOrder, "id", 200L);
        bobOrder.setStatus(OrderStatus.CREATED);
        bobOrder.setSupervisor(supBob);

        User operator1 = new User("op1", "op1@isd.com", "pass", Role.ROLE_OPERATOR, true, null, null);
        operator1.setId(101L);
        operator1.setIsActive(true);

        when(userRepository.findByUserRoleAndIsActiveTrue(Role.ROLE_OPERATOR)).thenReturn(List.of(operator1));
        when(orderRepository.findAll()).thenReturn(List.of(samOrder, bobOrder));
        when(replenishmentRepository.findAll()).thenReturn(List.of());

        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);
        when(securityFacade.getCurrentUserId()).thenReturn(10L);
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_sam");

        String result = tools.autoDistributeWorkload();

        assertThat(result).contains("Assigned 1 Orders");
        verify(orderService).assignOrder(samOrder.getId(), 101L);
        verify(orderService, never()).assignOrder(bobOrder.getId(), 101L);
    }

    @Test
    @DisplayName("DEF-29: WarehouseAiTools.calculateInitialWorkload balances based on both active orders and active replenishments")
    void def29_warehouseAiTools_calculateInitialWorkload_balancesOrdersAndReplenishments() {
        authenticateAs("dev_alice", "ROLE_DEV");
        WarehouseAiTools tools = new WarehouseAiTools(
            locationRepository,
            userRepository,
            orderRepository,
            replenishmentRepository,
            orderService,
            null,
            securityBoundary,
            securityFacade
        );

        User op1 = new User("op1", "op1@isd.com", "pass", Role.ROLE_OPERATOR, true, null, null);
        op1.setId(101L);
        op1.setIsActive(true);

        User op2 = new User("op2", "op2@isd.com", "pass", Role.ROLE_OPERATOR, true, null, null);
        op2.setId(102L);
        op2.setIsActive(true);

        // op1 is already busy with an active order (ASSIGNED)
        Order activeOrder = new Order("ORD-ACTIVE");
        ReflectionTestUtils.setField(activeOrder, "id", 50L);
        activeOrder.setStatus(OrderStatus.ASSIGNED);

        // unassigned new order
        Order unassignedOrder = new Order("ORD-NEW");
        ReflectionTestUtils.setField(unassignedOrder, "id", 51L);
        unassignedOrder.setStatus(OrderStatus.CREATED);

        when(userRepository.findByUserRoleAndIsActiveTrue(Role.ROLE_OPERATOR)).thenReturn(List.of(op1, op2));
        when(orderRepository.findAll()).thenReturn(List.of(activeOrder, unassignedOrder));
        when(replenishmentRepository.findAll()).thenReturn(List.of());

        // op1 is mapped to the active order
        com.isd.wms.repository.projections.OrderOperatorProjection opProj = mock(com.isd.wms.repository.projections.OrderOperatorProjection.class);
        when(opProj.getOrderId()).thenReturn(50L);
        when(opProj.getOperatorId()).thenReturn(101L);
        when(orderRepository.findOperatorIdsByOrderIds(List.of(50L))).thenReturn(List.of(opProj));

        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(true);

        String result = tools.autoDistributeWorkload();

        assertThat(result).contains("Assigned 1 Orders");
        // Because op1 has 1 active order and op2 has 0, op2 MUST get the new order!
        verify(orderService).assignOrder(51L, 102L);
        verify(orderService, never()).assignOrder(51L, 101L);
    }

    // =========================================================================
    // DEF-30: Strong Password, Log Redaction & Dashboard History Date Boundary
    // =========================================================================

    @Test
    @DisplayName("DEF-30: StrongPasswordValidator strictly validates complex passwords")
    void def30_strongPasswordValidator_validatesComplexityRules() {
        assertThat(StrongPasswordValidator.isStrongPassword(null)).isFalse();
        assertThat(StrongPasswordValidator.isStrongPassword("")).isFalse();
        assertThat(StrongPasswordValidator.isStrongPassword("short1!")).isFalse(); // < 8
        assertThat(StrongPasswordValidator.isStrongPassword("nouppercase123!")).isFalse();
        assertThat(StrongPasswordValidator.isStrongPassword("NOLOWERCASE123!")).isFalse();
        assertThat(StrongPasswordValidator.isStrongPassword("NoDigits!@#")).isFalse();
        assertThat(StrongPasswordValidator.isStrongPassword("NoSpecial1234")).isFalse();

        // Valid combinations with allowed special characters (@$!%*?&._#-)
        assertThat(StrongPasswordValidator.isStrongPassword("Password123!")).isTrue();
        assertThat(StrongPasswordValidator.isStrongPassword("Secret_99@Wms")).isTrue();
        assertThat(StrongPasswordValidator.isStrongPassword("Admin#2026.")).isTrue();
    }

    @Test
    @DisplayName("DEF-30: UserService.verifyEmail rejects weak passwords with IllegalArgumentException")
    void def30_userService_verifyEmail_weakPassword_throwsIllegalArgumentException() {
        UserService userService = new UserService(
            userRepository,
            mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            mock(EmailService.class),
            mock(com.isd.wms.mapper.UserMapper.class),
            securityFacade
        );

        assertThatThrownBy(() -> userService.verifyEmail("valid-token", "weakpass"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Password must be at least 8 characters long");
    }
}
