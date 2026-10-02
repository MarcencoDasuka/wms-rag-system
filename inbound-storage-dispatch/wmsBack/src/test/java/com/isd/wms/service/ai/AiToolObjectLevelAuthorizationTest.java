package com.isd.wms.service.ai;

import com.isd.wms.dto.order.ExtendedOrderCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Zone;
import com.isd.wms.repository.*;
import com.isd.wms.service.InventoryAdjustmentService;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.ReplenishmentService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite verifying object-level authorization (BOLA/IDOR prevention)
 * across all mutating AI tools (Finding AI-2).
 */
@ExtendWith(MockitoExtension.class)
class AiToolObjectLevelAuthorizationTest {

    @Mock private SecurityFacade securityFacade;
    @Mock private OrderRepository orderRepository;
    @Mock private ReplenishmentRepository replenishmentRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockRepository stockRepository;
    @Mock private OrderService orderService;
    @Mock private ReplenishmentService replenishmentService;
    @Mock private InventoryService inventoryService;
    @Mock private InventoryAdjustmentService inventoryAdjustmentService;

    private AiToolSecurityBoundary securityBoundary;
    private OrderMutatingAiTools orderMutatingAiTools;
    private ReplenishmentAiTools replenishmentAiTools;
    private InventoryMutatingAiTools inventoryMutatingAiTools;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        securityBoundary = new AiToolSecurityBoundary(securityFacade, orderRepository);
        orderMutatingAiTools = new OrderMutatingAiTools(
            orderService, orderRepository, locationRepository, productRepository, userRepository, securityBoundary
        );
        replenishmentAiTools = new ReplenishmentAiTools(
            replenishmentService, replenishmentRepository, productRepository, locationRepository, userRepository, securityBoundary
        );
        inventoryMutatingAiTools = new InventoryMutatingAiTools(
            productRepository, stockRepository, locationRepository, userRepository,
            inventoryService, inventoryAdjustmentService, securityFacade, securityBoundary
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            username,
            "password",
            List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        lenient().when(securityFacade.getCurrentUsername()).thenReturn(username);
        lenient().when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn("ROLE_DEV".equalsIgnoreCase(role));
    }

    // =========================================================================
    // 1. Authorized User + Authorized Target -> Allowed
    // =========================================================================

    @Test
    @DisplayName("Authorized supervisor can delete their own order with confirmation")
    void deleteOrder_whenAuthorizedSupervisorOwnsOrder_succeedsWithConfirmation() {
        authenticateAs("supervisor_alice", "ROLE_SUPERVISOR");

        Order order = new Order("ORD-001");
        order.setId(10L);
        when(orderRepository.findByLogicIdIgnoreCase("ORD-001")).thenReturn(Optional.of(order));
        when(orderRepository.findSupervisorUsernamesByOrder(order)).thenReturn(List.of("supervisor_alice"));

        // Phase 1: Request confirmation
        String prompt = orderMutatingAiTools.deleteOrder("ORD-001", null);
        assertThat(prompt).contains("CONFIRMATION REQUIRED: DELETE_ORDER on target 'ORD-001'");

        String token = prompt.substring(prompt.indexOf("confirmationToken='") + 19, prompt.lastIndexOf("'"));

        // Phase 2: Execute with token
        String result = orderMutatingAiTools.deleteOrder("ORD-001", token);
        assertThat(result).contains("Success! Order ORD-001 has been deleted.");
        verify(orderService).deleteOrderById(10L);
    }

    @Test
    @DisplayName("Authorized supervisor can cancel their own replenishment")
    void cancelReplenishment_whenAuthorizedSupervisorOwnsReplenishment_succeeds() {
        authenticateAs("supervisor_alice", "ROLE_SUPERVISOR");

        Replenishment repl = new Replenishment();
        repl.setId(20L);
        repl.setLogicId("REPL-001");
        User supervisor = new User();
        supervisor.setUsername("supervisor_alice");
        Task task = new Task();
        task.setSupervisor(supervisor);
        repl.setTask(task);

        when(replenishmentRepository.findByLogicIdIgnoreCase("REPL-001")).thenReturn(Optional.of(repl));

        String result = replenishmentAiTools.cancelReplenishmentTask("REPL-001");
        assertThat(result).contains("Success! Replenishment REPL-001 has been canceled");
        verify(replenishmentService).cancelReplenishment(20L);
    }

    // =========================================================================
    // 2. Authorized Role + Unauthorized Target -> Denied (BOLA / IDOR)
    // =========================================================================

    @Test
    @DisplayName("Supervisor attempting to delete another supervisor's order is DENIED")
    void deleteOrder_whenOrderBelongsToOtherSupervisor_deniedWithAccessDeniedException() {
        authenticateAs("supervisor_alice", "ROLE_SUPERVISOR");

        Order order = new Order("ORD-OTHER");
        order.setId(99L);
        when(orderRepository.findByLogicIdIgnoreCase("ORD-OTHER")).thenReturn(Optional.of(order));
        when(orderRepository.findSupervisorUsernamesByOrder(order)).thenReturn(List.of("supervisor_bob"));

        assertThatThrownBy(() -> orderMutatingAiTools.deleteOrder("ORD-OTHER", null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("belongs to another supervisor");

        verify(orderService, never()).deleteOrderById(any());
    }

    @Test
    @DisplayName("Supervisor attempting to cancel another supervisor's replenishment is DENIED")
    void cancelReplenishment_whenBelongsToOtherSupervisor_deniedWithAccessDeniedException() {
        authenticateAs("supervisor_alice", "ROLE_SUPERVISOR");

        Replenishment repl = new Replenishment();
        repl.setId(88L);
        repl.setLogicId("REPL-BOB");
        User supervisor = new User();
        supervisor.setUsername("supervisor_bob");
        Task task = new Task();
        task.setSupervisor(supervisor);
        repl.setTask(task);

        when(replenishmentRepository.findByLogicIdIgnoreCase("REPL-BOB")).thenReturn(Optional.of(repl));

        assertThatThrownBy(() -> replenishmentAiTools.cancelReplenishmentTask("REPL-BOB"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("belongs to another supervisor");

        verify(replenishmentService, never()).cancelReplenishment(any());
    }

    @Test
    @DisplayName("Supervisor attempting to assign order to non-operator is DENIED")
    void assignOrderToOperator_whenTargetUserIsNotOperator_deniedWithAccessDeniedException() {
        authenticateAs("supervisor_alice", "ROLE_SUPERVISOR");

        Order order = new Order("ORD-001");
        when(orderRepository.findByLogicIdIgnoreCase("ORD-001")).thenReturn(Optional.of(order));
        when(orderRepository.findSupervisorUsernamesByOrder(order)).thenReturn(List.of("supervisor_alice"));

        User manager = new User();
        manager.setUsername("manager_dan");
        manager.setUserRole(Role.ROLE_SUPERVISOR);
        manager.setIsActive(true);
        when(userRepository.findAll()).thenReturn(List.of(manager));

        assertThatThrownBy(() -> orderMutatingAiTools.assignOrderToOperator("ORD-001", "manager_dan"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("is not an operator");

        verify(orderService, never()).assignOrder(any(), any());
    }

    // =========================================================================
    // 3. Operator Calling Mutating Tools -> Denied
    // =========================================================================

    @Test
    @DisplayName("Operator attempting to delete order is DENIED at role boundary")
    void deleteOrder_whenCallerIsOperator_denied() {
        authenticateAs("operator_charlie", "ROLE_OPERATOR");

        assertThatThrownBy(() -> orderMutatingAiTools.deleteOrder("ORD-001", null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("lacks required SUPERVISOR or DEV role");

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("Operator attempting to cancel replenishment is DENIED at role boundary")
    void cancelReplenishment_whenCallerIsOperator_denied() {
        authenticateAs("operator_charlie", "ROLE_OPERATOR");

        assertThatThrownBy(() -> replenishmentAiTools.cancelReplenishmentTask("REPL-001"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("lacks required SUPERVISOR or DEV role");

        verifyNoInteractions(replenishmentService);
    }

    @Test
    @DisplayName("Operator attempting to receive inbound stock is DENIED at role boundary")
    void receiveInboundStock_whenCallerIsOperator_denied() {
        authenticateAs("operator_charlie", "ROLE_OPERATOR");

        assertThatThrownBy(() -> inventoryMutatingAiTools.receiveInboundStock("BARCODE", 10, "LOC-1"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("lacks required SUPERVISOR or DEV role");

        verifyNoInteractions(inventoryService);
    }

    // =========================================================================
    // 4. DEV Behavior Matches Documented Policy (Admin Access Allowed)
    // =========================================================================

    @Test
    @DisplayName("DEV caller can delete order even if supervised by someone else (with confirmation)")
    void deleteOrder_whenDevCaller_allowsAccessAcrossSupervisors() {
        authenticateAs("dev_alice", "ROLE_DEV");

        Order order = new Order("ORD-ANY");
        order.setId(55L);
        when(orderRepository.findByLogicIdIgnoreCase("ORD-ANY")).thenReturn(Optional.of(order));

        // DEV requests confirmation
        String prompt = orderMutatingAiTools.deleteOrder("ORD-ANY", null);
        assertThat(prompt).contains("CONFIRMATION REQUIRED: DELETE_ORDER");

        String token = prompt.substring(prompt.indexOf("confirmationToken='") + 19, prompt.lastIndexOf("'"));

        // DEV executes confirmed deletion
        String result = orderMutatingAiTools.deleteOrder("ORD-ANY", token);
        assertThat(result).contains("Success! Order ORD-ANY has been deleted.");
        verify(orderService).deleteOrderById(55L);
    }

    @Test
    @DisplayName("DEV caller can cancel replenishment belonging to any supervisor")
    void cancelReplenishment_whenDevCaller_allowsAccessAcrossSupervisors() {
        authenticateAs("dev_alice", "ROLE_DEV");

        Replenishment repl = new Replenishment();
        repl.setId(77L);
        repl.setLogicId("REPL-BOB");
        User supervisor = new User();
        supervisor.setUsername("supervisor_bob");
        Task task = new Task();
        task.setSupervisor(supervisor);
        repl.setTask(task);

        when(replenishmentRepository.findByLogicIdIgnoreCase("REPL-BOB")).thenReturn(Optional.of(repl));

        String result = replenishmentAiTools.cancelReplenishmentTask("REPL-BOB");
        assertThat(result).contains("Success! Replenishment REPL-BOB has been canceled");
        verify(replenishmentService).cancelReplenishment(77L);
    }

    // =========================================================================
    // 5. Confirmation Mechanism from AI-1 Remains Intact
    // =========================================================================

    @Test
    @DisplayName("Confirmation token is required and single-use for high-impact mutation")
    void confirmationMechanism_remainsIntactAndSingleUse() {
        authenticateAs("supervisor_alice", "ROLE_SUPERVISOR");

        Order order = new Order("ORD-001");
        order.setId(10L);
        when(orderRepository.findByLogicIdIgnoreCase("ORD-001")).thenReturn(Optional.of(order));
        when(orderRepository.findSupervisorUsernamesByOrder(order)).thenReturn(List.of("supervisor_alice"));

        // Phase 1: Without token -> returns prompt
        String prompt = orderMutatingAiTools.deleteOrder("ORD-001", null);
        assertThat(prompt).contains("CONFIRMATION REQUIRED");

        String token = prompt.substring(prompt.indexOf("confirmationToken='") + 19, prompt.lastIndexOf("'"));

        // Phase 2: With valid token -> executes
        String result = orderMutatingAiTools.deleteOrder("ORD-001", token);
        assertThat(result).contains("Success! Order ORD-001 has been deleted.");

        // Phase 3: Replay with same token -> rejected
        assertThatThrownBy(() -> orderMutatingAiTools.deleteOrder("ORD-001", token))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Invalid or expired confirmation token");
    }

    // =========================================================================
    // 6. Zone-Level Target Constraints
    // =========================================================================

    @Test
    @DisplayName("createOrder validates that destination location must be in a DISPATCH zone")
    void createOrder_whenDestinationNotDispatch_returnsError() {
        authenticateAs("supervisor_alice", "ROLE_SUPERVISOR");

        Location storageLoc = new Location();
        storageLoc.setBarcode("LOC-STORAGE-1");
        storageLoc.setZone(Zone.REPLENISHMENT);

        when(locationRepository.findAll()).thenReturn(List.of(storageLoc));

        String result = orderMutatingAiTools.createOrder("ORD-DISPATCH-TEST", "LOC-STORAGE-1", List.of());
        assertThat(result).contains("Error: Destination location must be in a DISPATCH zone.");
        verify(orderService, never()).addExtendedOrder(any());
    }

    @Test
    @DisplayName("receiveInboundStock validates that destination cannot be a DISPATCH location")
    void receiveInboundStock_whenDestinationIsDispatch_returnsError() {
        authenticateAs("supervisor_alice", "ROLE_SUPERVISOR");

        Product product = new Product();
        product.setId(1L);
        product.setBarcode("PROD-1");

        Location dispatchLoc = new Location();
        dispatchLoc.setBarcode("LOC-DISP");
        dispatchLoc.setZone(Zone.DISPATCH);

        when(productRepository.findByBarcode("PROD-1")).thenReturn(Optional.of(product));
        when(locationRepository.findAll()).thenReturn(List.of(dispatchLoc));

        String result = inventoryMutatingAiTools.receiveInboundStock("PROD-1", 10, "LOC-DISP");
        assertThat(result).contains("Error: Cannot receive inbound stock into a DISPATCH location.");
        verify(inventoryService, never()).addStock(any());
    }
}
