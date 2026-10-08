package com.isd.wms.service.allocation;

import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Zone;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.*;
import com.isd.wms.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockReservationConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderLineRepository orderLineRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Long> orderIds;
    private List<Long> stockIds;
    private List<Long> productIds;
    private List<Long> locationIds;
    private List<Long> categoryIds;

    @BeforeEach
    void setUp() {
        orderIds = new ArrayList<>();
        stockIds = new ArrayList<>();
        productIds = new ArrayList<>();
        locationIds = new ArrayList<>();
        categoryIds = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                orderIds.forEach(orderId -> {
                    jdbcTemplate.update("UPDATE transport_units SET order_id = NULL WHERE order_id = ?", orderId);
                    List<Long> taskIds = jdbcTemplate.queryForList(
                        "SELECT task_id FROM order_lines WHERE order_id = ? AND task_id IS NOT NULL", Long.class, orderId
                    );
                    jdbcTemplate.update("UPDATE order_lines SET task_id = NULL WHERE order_id = ?", orderId);
                    taskIds.forEach(taskId -> {
                        jdbcTemplate.update("DELETE FROM allocations WHERE task_id = ?", taskId);
                        jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", taskId);
                    });
                    jdbcTemplate.update("DELETE FROM order_lines WHERE order_id = ?", orderId);
                    jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
                });
                stockIds.forEach(id -> jdbcTemplate.update("DELETE FROM stocks WHERE id = ?", id));
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
    @DisplayName("B-5: Concurrent order assignments competing for the same stock prevent overselling and maintain exact reservation integrity")
    void concurrentOrderAssignment_competingForStock_preventsOverselling() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Prepare test fixtures in a dedicated transaction
        Long[] fixtures = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            User supervisor = userRepository.findByUsername("supervisor")
                .orElseThrow(() -> new IllegalStateException("Seed supervisor not found"));
            User operator = userRepository.findByUsername("operator")
                .orElseThrow(() -> new IllegalStateException("Seed operator not found"));

            Category category = categoryRepository.save(new Category("CatB5_" + suffix));
            categoryIds.add(category.getId());
            Product product = productRepository.save(new Product("ProdB5_" + suffix, "BAR_B5_" + suffix, "Desc B5", category));
            productIds.add(product.getId());

            Location pickLoc = locationRepository.save(new Location("PLOC_B5_" + suffix, "BC_PLOC_B5_" + suffix, Zone.PICKING, "Pick B5"));
            locationIds.add(pickLoc.getId());
            Location destLoc = locationRepository.save(new Location("DLOC_B5_" + suffix, "BC_DLOC_B5_" + suffix, Zone.DISPATCH, "Dest B5"));
            locationIds.add(destLoc.getId());

            // Stock has quantity 10, reserved 0 -> available 10
            Stock stock = stockRepository.save(new Stock(product, pickLoc, 10, LocalDate.now(), LocalDate.now().plusYears(1)));
            stockIds.add(stock.getId());

            // Order 1 needs 7 units
            Order order1 = orderRepository.save(new Order("ORD1_B5_" + suffix, destLoc));
            orderIds.add(order1.getId());
            OrderLine line1 = orderLineRepository.save(new OrderLine(order1, product, 7));
            order1.setOrderLines(List.of(line1));

            // Order 2 needs 7 units (total demand 14 > available 10)
            Order order2 = orderRepository.save(new Order("ORD2_B5_" + suffix, destLoc));
            orderIds.add(order2.getId());
            OrderLine line2 = orderLineRepository.save(new OrderLine(order2, product, 7));
            order2.setOrderLines(List.of(line2));

            return new Long[]{
                stock.getId(),
                order1.getId(),
                order2.getId(),
                operator.getId(),
                supervisor.getId()
            };
        });

        assertThat(fixtures).isNotNull();
        Long stockId = fixtures[0];
        Long order1Id = fixtures[1];
        Long order2Id = fixtures[2];
        Long operatorId = fixtures[3];

        // 2. Launch concurrent order assignments
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        Runnable assignOrder1 = () -> {
            setupSecurityContext("supervisor", "ROLE_SUPERVISOR");
            try {
                barrier.await(5, TimeUnit.SECONDS);
                orderService.assignOrder(order1Id, operatorId);
                successCount.incrementAndGet();
            } catch (InvalidRequestException e) {
                if (e.getMessage() != null && e.getMessage().contains("Insufficient stock")) {
                    insufficientStockCount.incrementAndGet();
                } else {
                    otherErrors.incrementAndGet();
                }
            } catch (Exception e) {
                otherErrors.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
                latch.countDown();
            }
        };

        Runnable assignOrder2 = () -> {
            setupSecurityContext("supervisor", "ROLE_SUPERVISOR");
            try {
                barrier.await(5, TimeUnit.SECONDS);
                orderService.assignOrder(order2Id, operatorId);
                successCount.incrementAndGet();
            } catch (InvalidRequestException e) {
                if (e.getMessage() != null && e.getMessage().contains("Insufficient stock")) {
                    insufficientStockCount.incrementAndGet();
                } else {
                    otherErrors.incrementAndGet();
                }
            } catch (Exception e) {
                otherErrors.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
                latch.countDown();
            }
        };

        executor.submit(assignOrder1);
        executor.submit(assignOrder2);

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(completed).isTrue();
        assertThat(otherErrors.get()).as("No unexpected exceptions or deadlock errors should occur").isZero();
        assertThat(successCount.get()).as("Exactly one order should win the race and be assigned").isEqualTo(1);
        assertThat(insufficientStockCount.get()).as("The losing order should cleanly receive Insufficient stock exception").isEqualTo(1);

        // 3. Verify database state integrity
        tx.executeWithoutResult(status -> {
            Stock stock = stockRepository.findById(stockId).orElseThrow();
            assertThat(stock.getQuantity()).isEqualTo(10);
            assertThat(stock.getReservedQuantity()).isEqualTo(7);
            assertThat(stock.getAvailableQuantity()).isEqualTo(3);
            assertThat(stock.getReservedQuantity()).isLessThanOrEqualTo(stock.getQuantity());

            Order order1 = orderRepository.findById(order1Id).orElseThrow();
            Order order2 = orderRepository.findById(order2Id).orElseThrow();

            // One order is ASSIGNED, the other remained in CREATED status
            if (order1.getStatus() == OrderStatus.ASSIGNED) {
                assertThat(order2.getStatus()).isEqualTo(OrderStatus.CREATED);
            } else {
                assertThat(order1.getStatus()).isEqualTo(OrderStatus.CREATED);
                assertThat(order2.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            }

            // Total allocations created in the system for this stock must exactly equal 7
            List<Allocation> stockAllocations = allocationRepository.findAll().stream()
                .filter(a -> a.getStock().getId().equals(stockId))
                .toList();
            int totalAllocated = stockAllocations.stream().mapToInt(Allocation::getQuantity).sum();
            assertThat(totalAllocated).isEqualTo(7);
        });
    }

    @Test
    @DisplayName("B-5: Concurrent multi-line orders with opposite product ordering do not deadlock due to canonical sorting")
    void concurrentMultiLineOrders_withOppositeOrdering_doesNotDeadlock() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long[] fixtures = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            User supervisor = userRepository.findByUsername("supervisor")
                .orElseThrow(() -> new IllegalStateException("Seed supervisor not found"));
            User operator = userRepository.findByUsername("operator")
                .orElseThrow(() -> new IllegalStateException("Seed operator not found"));

            Category category = categoryRepository.save(new Category("CatDL_" + suffix));
            categoryIds.add(category.getId());
            Product prodA = productRepository.save(new Product("ProdA_" + suffix, "BAR_A_" + suffix, "Desc A", category));
            productIds.add(prodA.getId());
            Product prodB = productRepository.save(new Product("ProdB_" + suffix, "BAR_B_" + suffix, "Desc B", category));
            productIds.add(prodB.getId());

            Location pickLocA = locationRepository.save(new Location("PLOC_A_" + suffix, "BC_PLOC_A_" + suffix, Zone.PICKING, "Pick A"));
            locationIds.add(pickLocA.getId());
            Location pickLocB = locationRepository.save(new Location("PLOC_B_" + suffix, "BC_PLOC_B_" + suffix, Zone.PICKING, "Pick B"));
            locationIds.add(pickLocB.getId());
            Location destLoc = locationRepository.save(new Location("DLOC_DL_" + suffix, "BC_DLOC_DL_" + suffix, Zone.DISPATCH, "Dest DL"));
            locationIds.add(destLoc.getId());

            // Stock with plenty of inventory for both orders
            Stock stockA = stockRepository.save(new Stock(prodA, pickLocA, 50, LocalDate.now(), LocalDate.now().plusYears(1)));
            stockIds.add(stockA.getId());
            Stock stockB = stockRepository.save(new Stock(prodB, pickLocB, 50, LocalDate.now(), LocalDate.now().plusYears(1)));
            stockIds.add(stockB.getId());

            // Order 1 lines: [ProdA, ProdB]
            Order order1 = orderRepository.save(new Order("ORD1_DL_" + suffix, destLoc));
            orderIds.add(order1.getId());
            OrderLine line1A = orderLineRepository.save(new OrderLine(order1, prodA, 5));
            OrderLine line1B = orderLineRepository.save(new OrderLine(order1, prodB, 5));
            order1.setOrderLines(List.of(line1A, line1B));

            // Order 2 lines with opposite order: [ProdB, ProdA]
            Order order2 = orderRepository.save(new Order("ORD2_DL_" + suffix, destLoc));
            orderIds.add(order2.getId());
            OrderLine line2B = orderLineRepository.save(new OrderLine(order2, prodB, 5));
            OrderLine line2A = orderLineRepository.save(new OrderLine(order2, prodA, 5));
            order2.setOrderLines(List.of(line2B, line2A));

            return new Long[]{
                stockA.getId(),
                stockB.getId(),
                order1.getId(),
                order2.getId(),
                operator.getId()
            };
        });

        assertThat(fixtures).isNotNull();
        Long stockAId = fixtures[0];
        Long stockBId = fixtures[1];
        Long order1Id = fixtures[2];
        Long order2Id = fixtures[3];
        Long operatorId = fixtures[4];

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        Runnable assignOrder1 = () -> {
            setupSecurityContext("supervisor", "ROLE_SUPERVISOR");
            try {
                barrier.await(5, TimeUnit.SECONDS);
                orderService.assignOrder(order1Id, operatorId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
                latch.countDown();
            }
        };

        Runnable assignOrder2 = () -> {
            setupSecurityContext("supervisor", "ROLE_SUPERVISOR");
            try {
                barrier.await(5, TimeUnit.SECONDS);
                orderService.assignOrder(order2Id, operatorId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
                latch.countDown();
            }
        };

        executor.submit(assignOrder1);
        executor.submit(assignOrder2);

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(completed).isTrue();
        assertThat(errorCount.get()).as("Neither transaction should fail with deadlock").isZero();
        assertThat(successCount.get()).as("Both orders should successfully be assigned").isEqualTo(2);

        tx.executeWithoutResult(status -> {
            Stock stockA = stockRepository.findById(stockAId).orElseThrow();
            Stock stockB = stockRepository.findById(stockBId).orElseThrow();

            assertThat(stockA.getReservedQuantity()).isEqualTo(10);
            assertThat(stockB.getReservedQuantity()).isEqualTo(10);

            Order order1 = orderRepository.findById(order1Id).orElseThrow();
            Order order2 = orderRepository.findById(order2Id).orElseThrow();

            assertThat(order1.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(order2.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        });
    }

    private void setupSecurityContext(String username, String role) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
            username,
            "N/A",
            List.of(new SimpleGrantedAuthority(role))
        ));
        SecurityContextHolder.setContext(context);
    }
}
