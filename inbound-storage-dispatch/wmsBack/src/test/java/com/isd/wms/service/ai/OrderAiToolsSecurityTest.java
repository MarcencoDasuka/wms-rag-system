package com.isd.wms.service.ai;

import com.isd.wms.dto.order.ExtendedOrderCreateRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.User;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.OrderService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAiToolsSecurityTest {

    @Mock private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiToolSecurityBoundary securityBoundary;

    private OrderMutatingAiTools orderMutatingAiTools;
    private OrderAiTools orderAiTools;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        orderMutatingAiTools = new OrderMutatingAiTools(
            orderService,
            orderRepository,
            locationRepository,
            productRepository,
            userRepository,
            securityBoundary
        );
        orderAiTools = new OrderAiTools(
            orderService,
            orderRepository,
            orderMutatingAiTools
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getActiveOrdersInfo is read-only and requires neither mutation permission nor confirmation")
    void getActiveOrdersInfo_readOnly_succeedsWithoutMutationBoundary() {
        Order sample = new Order("ORD-001", null);
        sample.setStatus(OrderStatus.CREATED);
        when(orderRepository.findAll()).thenReturn(List.of(sample));
        when(orderRepository.findOperatorUsernameByOrder(any())).thenReturn(Optional.of("operator1"));

        String result = orderAiTools.getActiveOrdersInfo();

        assertThat(result).contains("ORD-001");
        verifyNoInteractions(orderService);
        verifyNoInteractions(securityBoundary);
    }

    @Test
    @DisplayName("deleteOrder delegates to securityBoundary and throws AccessDeniedException when caller is unauthorized")
    void deleteOrder_whenUnauthorized_throwsAccessDeniedException() {
        doThrow(new AccessDeniedException("Access denied: User lacks required SUPERVISOR or DEV role"))
            .when(securityBoundary).enforceSupervisorOrDev("deleteOrder");

        assertThatThrownBy(() -> orderMutatingAiTools.deleteOrder("ORD-001", null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Access denied");

        verify(orderService, never()).deleteOrderById(any());
    }

    @Test
    @DisplayName("deleteOrder requires confirmation token when caller is authorized but token is not supplied")
    void deleteOrder_whenAuthorizedWithoutToken_returnsConfirmationPrompt() {
        Order order = new Order("ORD-001", null);
        when(orderRepository.findByLogicIdIgnoreCase("ORD-001")).thenReturn(Optional.of(order));
        when(securityBoundary.requireConfirmation(eq("DELETE_ORDER"), eq("ORD-001"), any(), isNull()))
            .thenReturn("CONFIRMATION REQUIRED: DELETE_ORDER on target 'ORD-001'. To confirm, call with confirmationToken='CONFIRM-TEST'.");

        String response = orderMutatingAiTools.deleteOrder("ORD-001", null);

        assertThat(response).contains("CONFIRMATION REQUIRED");
        assertThat(response).contains("confirmationToken='CONFIRM-TEST'");
        verify(orderService, never()).deleteOrderById(any());
    }

    @Test
    @DisplayName("deleteOrder executes deletion and releases stock when valid confirmation token is provided")
    void deleteOrder_whenAuthorizedWithValidToken_executesDeletion() {
        Order order = new Order("ORD-001", null);
        order.setId(42L);
        when(orderRepository.findByLogicIdIgnoreCase("ORD-001")).thenReturn(Optional.of(order));
        // Valid token returns null from requireConfirmation
        when(securityBoundary.requireConfirmation(eq("DELETE_ORDER"), eq("ORD-001"), any(), eq("CONFIRM-VALID")))
            .thenReturn(null);

        String response = orderMutatingAiTools.deleteOrder("ORD-001", "CONFIRM-VALID");

        assertThat(response).contains("Success! Order ORD-001 has been deleted.");
        verify(orderService).deleteOrderById(42L);
        verify(securityBoundary).auditMutation(eq("deleteOrder"), eq("ORD-001"), any());
    }

    @Test
    @DisplayName("createOrder enforces authorization boundary and audits successful creation")
    void createOrder_whenAuthorized_executesAndAudits() {
        Location location = new Location();
        location.setId(10L);
        location.setBarcode("LOC-DISPATCH-1");
        Product product = new Product();
        product.setId(20L);
        product.setName("Apple");
        product.setBarcode("APL-01");

        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(productRepository.findByBarcode("APL-01")).thenReturn(Optional.of(product));

        String response = orderMutatingAiTools.createOrder(
            "ORD-NEW-1",
            location.getBarcode(),
            List.of(new OrderAiTools.AiOrderItem("APL-01", 5))
        );

        assertThat(response).contains("Success! Order 'ORD-NEW-1' created");
        verify(securityBoundary).enforceSupervisorOrDev("createOrder");
        verify(orderService).addExtendedOrder(any(ExtendedOrderCreateRequest.class));
        verify(securityBoundary).auditMutation(eq("createOrder"), eq("ORD-NEW-1"), any());
    }

    @Test
    @DisplayName("createOrder throws AccessDeniedException when caller is unauthorized")
    void createOrder_whenUnauthorized_throwsAccessDeniedException() {
        doThrow(new AccessDeniedException("Access denied"))
            .when(securityBoundary).enforceSupervisorOrDev("createOrder");

        assertThatThrownBy(() -> orderMutatingAiTools.createOrder("ORD-1", "LOC-1", List.of()))
            .isInstanceOf(AccessDeniedException.class);

        verify(orderService, never()).addExtendedOrder(any());
    }

    @Test
    @DisplayName("deleteOrder throws AccessDeniedException when order belongs to another supervisor")
    void deleteOrder_whenOrderBelongsToOtherSupervisor_throwsAccessDeniedException() {
        Order order = new Order("ORD-001", null);
        when(orderRepository.findByLogicIdIgnoreCase("ORD-001")).thenReturn(Optional.of(order));
        doThrow(new AccessDeniedException("Access denied: Order belongs to another supervisor"))
            .when(securityBoundary).enforceOrderAccess(order);

        assertThatThrownBy(() -> orderMutatingAiTools.deleteOrder("ORD-001", "CONFIRM-123"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("belongs to another supervisor");

        verify(orderService, never()).deleteOrderById(any());
    }

    @Test
    @DisplayName("assignOrderToOperator throws AccessDeniedException when target user is not an operator")
    void assignOrderToOperator_whenTargetNotOperator_throwsAccessDeniedException() {
        Order order = new Order("ORD-001", null);
        User supervisor = new User();
        supervisor.setUsername("supervisor_other");

        when(orderRepository.findByLogicIdIgnoreCase("ORD-001")).thenReturn(Optional.of(order));
        when(userRepository.findAll()).thenReturn(List.of(supervisor));
        doThrow(new AccessDeniedException("Access denied: User is not an operator"))
            .when(securityBoundary).enforceTargetOperator(supervisor);

        assertThatThrownBy(() -> orderMutatingAiTools.assignOrderToOperator("ORD-001", "supervisor_other"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("is not an operator");

        verify(orderService, never()).assignOrder(any(), any());
    }
}
