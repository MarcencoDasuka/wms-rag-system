package com.isd.wms.service;

import com.isd.wms.dto.order.OrderCreateRequest;
import com.isd.wms.dto.order.OrderUpdateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.entity.Category;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Zone;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.CategoryRepository;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LogicIdUniquenessIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReplenishmentRepository replenishmentRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReplenishmentService replenishmentService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location testLocation;
    private Product testProduct;

    private List<Long> replenishmentIds;
    private List<Long> orderIds;
    private List<Long> productIds;
    private List<Long> locationIds;
    private List<Long> categoryIds;

    @BeforeEach
    void setUp() {
        replenishmentIds = new ArrayList<>();
        orderIds = new ArrayList<>();
        productIds = new ArrayList<>();
        locationIds = new ArrayList<>();
        categoryIds = new ArrayList<>();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Category cat = categoryRepository.save(new Category("Cat_D3_" + suffix));
            categoryIds.add(cat.getId());
            testProduct = productRepository.save(new Product("Prod_D3_" + suffix, "BAR_D3_" + suffix, "Desc", cat));
            productIds.add(testProduct.getId());
            testLocation = locationRepository.save(new Location("LOC_D3_" + suffix, "BC_D3_" + suffix, Zone.PICKING, "Desc"));
            locationIds.add(testLocation.getId());
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                replenishmentIds.forEach(id -> jdbcTemplate.update("DELETE FROM replenishments WHERE id = ?", id));
                orderIds.forEach(id -> jdbcTemplate.update("DELETE FROM orders WHERE id = ?", id));
                productIds.forEach(id -> jdbcTemplate.update("DELETE FROM products WHERE id = ?", id));
                locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM locations WHERE id = ?", id));
                categoryIds.forEach(id -> jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id));
                return null;
            });
        } catch (Exception e) {
            System.err.println("Teardown failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("D-3: Replenishment logic_id uniqueness and case-insensitivity are enforced by DB constraint and index")
    void replenishmentLogicId_enforcesCaseInsensitiveUniqueness() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String uniqueLogicId = "REPL-UNIQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Location loc2 = locationRepository.save(new Location("LOC_D3_B_" + UUID.randomUUID().toString().substring(0, 8), "BC_D3_B_" + UUID.randomUUID().toString().substring(0, 8), Zone.PICKING, "Desc"));
        locationIds.add(loc2.getId());
        Location loc3 = locationRepository.save(new Location("LOC_D3_C_" + UUID.randomUUID().toString().substring(0, 8), "BC_D3_C_" + UUID.randomUUID().toString().substring(0, 8), Zone.PICKING, "Desc"));
        locationIds.add(loc3.getId());

        // 1. First replenishment with unique logicId persists successfully
        tx.execute(status -> {
            Replenishment r1 = new Replenishment(testProduct, 10, testLocation);
            r1.setLogicId(uniqueLogicId);
            r1 = replenishmentRepository.save(r1);
            replenishmentIds.add(r1.getId());
            return null;
        });

        // 2. Inserting another replenishment with the exact same logicId fails at DB level due to uk_replenishments_logic_id
        assertThatThrownBy(() -> tx.execute(status -> {
            Replenishment r2 = new Replenishment(testProduct, 5, loc2);
            r2.setLogicId(uniqueLogicId);
            replenishmentRepository.save(r2);
            replenishmentRepository.flush();
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class)
           .hasMessageContaining("uk_replenishments_logic_id");

        // 3. Inserting another replenishment with differing case (lowercase) fails due to LOWER(logic_id) unique index
        assertThatThrownBy(() -> tx.execute(status -> {
            Replenishment r3 = new Replenishment(testProduct, 5, loc3);
            r3.setLogicId(uniqueLogicId.toLowerCase());
            replenishmentRepository.save(r3);
            replenishmentRepository.flush();
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class)
           .hasMessageContaining("uk_replenishments_logic_id_lower");
    }

    @Test
    @DisplayName("D-3: Order logic_id uniqueness and case-insensitivity are enforced by DB constraint and index")
    void orderLogicId_enforcesCaseInsensitiveUniqueness() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String uniqueLogicId = "ORD-UNIQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 1. First order persists successfully
        tx.execute(status -> {
            Order o1 = new Order(uniqueLogicId, testLocation);
            o1 = orderRepository.save(o1);
            orderIds.add(o1.getId());
            return null;
        });

        // 2. Duplicate order with exact same logicId fails at DB level
        assertThatThrownBy(() -> tx.execute(status -> {
            Order o2 = new Order(uniqueLogicId, testLocation);
            orderRepository.save(o2);
            orderRepository.flush();
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class)
           .hasMessageContaining("orders_logic_id_key");

        // 3. Duplicate order differing only in case fails at DB level
        assertThatThrownBy(() -> tx.execute(status -> {
            Order o3 = new Order(uniqueLogicId.toLowerCase(), testLocation);
            orderRepository.save(o3);
            orderRepository.flush();
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class)
           .hasMessageContaining("uk_orders_logic_id_lower");
    }

    @Test
    @DisplayName("D-3: OrderService validates case-insensitive logicId collisions on create and update")
    void orderService_validatesCaseInsensitiveLogicIdUniqueness() {
        String baseLogicId = "ORD-SRV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 1. Create initial order
        Order order = orderService.addOrder(new OrderCreateRequest(baseLogicId, testLocation.getId()));
        orderIds.add(order.getId());
        assertThat(order.getLogicId()).isEqualTo(baseLogicId);

        // 2. Creating an order with different casing (lowercase) is rejected at service layer
        assertThatThrownBy(() -> orderService.addOrder(new OrderCreateRequest(baseLogicId.toLowerCase(), testLocation.getId())))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("already exists");

        // 3. Updating a second order to have the logicId of the first order is rejected at service layer
        Order secondOrder = orderService.addOrder(new OrderCreateRequest(null, testLocation.getId()));
        orderIds.add(secondOrder.getId());
        assertThatThrownBy(() -> orderService.updateOrder(
            secondOrder.getId(),
            new OrderUpdateRequest(baseLogicId.toLowerCase(), testLocation.getId(), OrderStatus.CREATED)
        ))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("D-3: ReplenishmentService generates unique logicId and lookups by case-insensitive logicId succeed")
    void replenishmentService_generatesUniqueLogicId_andFindsByLogicId() {
        ReplenishmentResponse response = replenishmentService.createReplenishment(
            new ReplenishmentCreateRequest(testProduct.getId(), 10, testLocation.getId())
        );
        replenishmentIds.add(response.id());

        assertThat(response.logicId()).isNotNull().startsWith("REPL-");

        Replenishment found = replenishmentRepository.findByLogicIdIgnoreCase(response.logicId().toLowerCase())
            .orElseThrow();
        assertThat(found.getId()).isEqualTo(response.id());
    }
}
