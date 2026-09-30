package com.isd.wms.service;

import com.isd.wms.dto.dashboard.CompletedOrdersTrendResponse;
import com.isd.wms.dto.dashboard.OperatorPerformanceResponse;
import com.isd.wms.dto.dashboard.SupervisorDashboardResponse;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.Task;
import com.isd.wms.entity.User;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.TaskType;
import com.isd.wms.repository.InventoryHistoryRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.TaskRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.validation.SecurityFacade;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisorDashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private StockRepository stockRepository;
    @Mock private InventoryHistoryRepository inventoryHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private SecurityFacade securityFacade;

    @InjectMocks
    private SupervisorDashboardService supervisorDashboardService;

    @Test
    void getDashboard_countsPartiallyCompletedOrdersAsCompletedInTrendAndPerformance() {
        User supervisor = user("supervisor", 100L, Role.ROLE_SUPERVISOR);
        User operator = user("operator-1", 200L, Role.ROLE_OPERATOR);
        LocalDateTime now = LocalDateTime.now();

        Order completedOrder = order(1L, "ORD-001", OrderStatus.COMPLETED, now.minusHours(2), now.minusHours(1));
        Order partiallyCompletedOrder = order(2L, "ORD-002", OrderStatus.PARTIALLY_COMPLETED, now.minusHours(3), now.minusMinutes(30));

        Task task = new Task(supervisor, TaskType.PICKING_ORDER, 1);
        ReflectionTestUtils.setField(task, "id", 1L);
        task.setOperator(operator);

        when(securityFacade.getCurrentUsername()).thenReturn(supervisor.getUsername());
        when(orderRepository.findAllByCreatedByUsername(supervisor.getUsername()))
                .thenReturn(List.of(completedOrder, partiallyCompletedOrder));
        when(taskRepository.findAll()).thenReturn(List.of(task));
        when(stockRepository.findAllByAvailableIsTrue()).thenReturn(List.of());
        when(inventoryHistoryRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAllByIsActiveTrue()).thenReturn(List.of(operator));
        when(orderRepository.isOrderAssignedToOperator(completedOrder, operator.getId())).thenReturn(true);
        when(orderRepository.isOrderAssignedToOperator(partiallyCompletedOrder, operator.getId())).thenReturn(true);

        SupervisorDashboardResponse dashboard = supervisorDashboardService.getDashboard();

        assertThat(dashboard.summary().completedOrdersToday()).isEqualTo(2);
        assertThat(dashboard.completedOrdersTrend())
                .extracting(CompletedOrdersTrendResponse::date, CompletedOrdersTrendResponse::count)
                .contains(tuple(LocalDate.now(), 2L));
        assertThat(dashboard.operatorPerformance())
                .extracting(OperatorPerformanceResponse::operatorName, OperatorPerformanceResponse::completedOrdersToday)
                .contains(tuple(operator.getUsername(), 2L));
    }

    private User user(String username, Long id, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("secret");
        user.setUserRole(role);
        return user;
    }

    private Order order(Long id, String logicId, OrderStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        Order order = new Order(logicId);
        ReflectionTestUtils.setField(order, "id", id);
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(updatedAt);
        return order;
    }
}
