package com.isd.wms.service;

import com.isd.wms.dto.order.ExtendedOrderResponse;
import com.isd.wms.dto.order.OrderResponse;
import com.isd.wms.dto.order.OrderSearchRequest;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NPlusOneQueryPerformanceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReplenishmentService replenishmentService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("D-5: Measure queries for Replenishment listing")
    void measureReplenishmentQueries() {
        statistics.clear();
        List<ReplenishmentResponse> result = replenishmentService.getAllReplenishments();
        long queries = statistics.getPrepareStatementCount();
        System.out.println("REPLENISHMENT COUNT: " + result.size() + ", QUERIES EXECUTED: " + queries);
        assertThat(result).isNotEmpty();
        assertThat(queries).as("Replenishment list queries must be batch fetched and <= 3").isLessThanOrEqualTo(3);
    }

    @Test
    @Transactional(readOnly = true)
    @WithMockUser(username = "admin", roles = {"SUPERVISOR"})
    @DisplayName("D-5: Measure queries for searchOrders")
    void measureOrderSearchQueries() {
        statistics.clear();
        List<OrderResponse> result = orderService.searchOrders(new OrderSearchRequest(null, null, null, null, null, null));
        long queries = statistics.getPrepareStatementCount();
        System.out.println("ORDER SEARCH COUNT: " + result.size() + ", QUERIES EXECUTED: " + queries);
        assertThat(result).isNotEmpty();
        assertThat(queries).as("Order search queries must be batch fetched and <= 5").isLessThanOrEqualTo(5);
    }

    @Test
    @Transactional(readOnly = true)
    @WithMockUser(username = "admin", roles = {"SUPERVISOR"})
    @DisplayName("D-5: Measure queries for getAllExtendedOrders")
    void measureExtendedOrderQueries() {
        statistics.clear();
        List<ExtendedOrderResponse> result = orderService.getAllExtendedOrders();
        long queries = statistics.getPrepareStatementCount();
        System.out.println("EXTENDED ORDER COUNT: " + result.size() + ", QUERIES EXECUTED: " + queries);
        assertThat(result).isNotEmpty();
        assertThat(queries).as("Extended orders queries must be batch fetched and <= 10").isLessThanOrEqualTo(10);
    }
}
