package com.isd.wms.job;

import com.isd.wms.entity.Location;
import com.isd.wms.entity.Order;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DataCleanupProtectionIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Test
    @DisplayName("Active orders must NOT be deleted by cleanup, while terminal orders are deleted")
    void deleteOrdersOlderThan_protectsActiveOrdersAndDeletesTerminalOrders() {
        Location location = locationRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Seed location not found"));

        // Create active orders
        Order activeOrder = new Order("ACT-" + UUID.randomUUID().toString().substring(0, 8), location);
        activeOrder.setStatus(OrderStatus.CREATED);
        activeOrder = orderRepository.saveAndFlush(activeOrder);

        Order inProgressOrder = new Order("INP-" + UUID.randomUUID().toString().substring(0, 8), location);
        inProgressOrder.setStatus(OrderStatus.IN_PROGRESS);
        inProgressOrder = orderRepository.saveAndFlush(inProgressOrder);

        Order shortageOrder = new Order("SHT-" + UUID.randomUUID().toString().substring(0, 8), location);
        shortageOrder.setStatus(OrderStatus.SHORTAGE);
        shortageOrder = orderRepository.saveAndFlush(shortageOrder);

        // Create terminal orders
        Order completedOrder = new Order("CMP-" + UUID.randomUUID().toString().substring(0, 8), location);
        completedOrder.setStatus(OrderStatus.COMPLETED);
        completedOrder = orderRepository.saveAndFlush(completedOrder);

        Order canceledOrder = new Order("CNC-" + UUID.randomUUID().toString().substring(0, 8), location);
        canceledOrder.setStatus(OrderStatus.CANCELED);
        canceledOrder = orderRepository.saveAndFlush(canceledOrder);

        Order partialOrder = new Order("PRT-" + UUID.randomUUID().toString().substring(0, 8), location);
        partialOrder.setStatus(OrderStatus.PARTIALLY_COMPLETED);
        partialOrder = orderRepository.saveAndFlush(partialOrder);

        // Cutoff in the future so that all test orders qualify by date
        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(5);

        int deleted = orderRepository.deleteOrdersOlderThan(cutoff);
        assertThat(deleted).isGreaterThanOrEqualTo(3);

        // Active orders MUST survive cleanup
        assertThat(orderRepository.findById(activeOrder.getId())).isPresent();
        assertThat(orderRepository.findById(inProgressOrder.getId())).isPresent();
        assertThat(orderRepository.findById(shortageOrder.getId())).isPresent();

        // Terminal orders MUST be cleaned up
        assertThat(orderRepository.findById(completedOrder.getId())).isEmpty();
        assertThat(orderRepository.findById(canceledOrder.getId())).isEmpty();
        assertThat(orderRepository.findById(partialOrder.getId())).isEmpty();
    }
}
