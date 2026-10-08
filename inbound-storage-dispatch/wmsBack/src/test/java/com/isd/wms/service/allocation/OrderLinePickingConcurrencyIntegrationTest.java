package com.isd.wms.service.allocation;

import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskStatus;
import com.isd.wms.enums.TaskType;
import com.isd.wms.enums.Zone;
import com.isd.wms.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderLinePickingConcurrencyIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderLineRepository orderLineRepository;

    @Autowired
    private TaskRepository taskRepository;

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
    private PickingOperatorStrategy pickingOperatorStrategy;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Long> allocationIds;
    private List<Long> orderLineIds;
    private List<Long> orderIds;
    private List<Long> stockIds;
    private List<Long> taskIds;
    private List<Long> locationIds;
    private List<Long> productIds;
    private List<Long> categoryIds;

    @BeforeEach
    void setUp() {
        allocationIds = new ArrayList<>();
        orderLineIds = new ArrayList<>();
        orderIds = new ArrayList<>();
        stockIds = new ArrayList<>();
        taskIds = new ArrayList<>();
        locationIds = new ArrayList<>();
        productIds = new ArrayList<>();
        categoryIds = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> {
                allocationIds.forEach(id -> jdbcTemplate.update("DELETE FROM allocations WHERE id = ?", id));
                orderLineIds.forEach(id -> jdbcTemplate.update("DELETE FROM order_lines WHERE id = ?", id));
                stockIds.forEach(id -> jdbcTemplate.update("DELETE FROM stocks WHERE id = ?", id));
                orderIds.forEach(id -> jdbcTemplate.update("DELETE FROM orders WHERE id = ?", id));
                taskIds.forEach(id -> jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", id));
                locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM locations WHERE id = ?", id));
                productIds.forEach(id -> jdbcTemplate.update("DELETE FROM products WHERE id = ?", id));
                categoryIds.forEach(id -> jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id));
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("Concurrent picking completion on the same OrderLine preserves deliveredQuantity without lost updates")
    void concurrentPicking_onSameOrderLine_preservesDeliveredQuantityWithoutLostUpdates() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Prepare test fixtures in a dedicated transaction
        Long[] ids = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            User operator = userRepository.findByUsername("operator")
                .orElseThrow(() -> new IllegalStateException("Seed operator not found"));
            User supervisor = userRepository.findByUsername("supervisor")
                .orElseThrow(() -> new IllegalStateException("Seed supervisor not found"));

            Category cat = categoryRepository.save(new Category("Cat_" + suffix));
            categoryIds.add(cat.getId());
            Product prod = productRepository.save(new Product("Prod_" + suffix, "BAR_" + suffix, "Desc", cat));
            productIds.add(prod.getId());

            Location loc1 = locationRepository.save(new Location("L1_" + suffix, "BC1_" + suffix, Zone.PICKING, "Loc 1"));
            locationIds.add(loc1.getId());
            Location loc2 = locationRepository.save(new Location("L2_" + suffix, "BC2_" + suffix, Zone.PICKING, "Loc 2"));
            locationIds.add(loc2.getId());
            Location destLoc = locationRepository.save(new Location("DST_" + suffix, "BCD_" + suffix, Zone.DISPATCH, "Dest Loc"));
            locationIds.add(destLoc.getId());

            Stock stock1 = stockRepository.save(new Stock(prod, loc1, 10, LocalDate.now(), LocalDate.now().plusYears(1)));
            stock1.setReservedQuantity(5);
            stock1 = stockRepository.save(stock1);
            stockIds.add(stock1.getId());

            Stock stock2 = stockRepository.save(new Stock(prod, loc2, 10, LocalDate.now(), LocalDate.now().plusYears(1)));
            stock2.setReservedQuantity(5);
            stock2 = stockRepository.save(stock2);
            stockIds.add(stock2.getId());

            Task task = new Task(supervisor, TaskType.PICKING_ORDER, 10);
            task.setOperator(operator);
            task.setStatus(TaskStatus.IN_PROGRESS);
            task = taskRepository.save(task);
            taskIds.add(task.getId());

            Order order = orderRepository.save(new Order("ORD-" + suffix, destLoc));
            order.setStatus(OrderStatus.IN_PROGRESS);
            order = orderRepository.save(order);
            orderIds.add(order.getId());

            OrderLine orderLine = new OrderLine(order, task, prod, 10);
            orderLine.setDeliveredQuantity(0);
            orderLine.setShortageQuantity(0);
            orderLine.setStatus(Status.IN_PROGRESS);
            orderLine = orderLineRepository.save(orderLine);
            orderLineIds.add(orderLine.getId());

            Allocation alloc1 = new Allocation(task, stock1, 5, Status.IN_PROGRESS);
            alloc1.setPickedQuantity(5);
            alloc1.setSourceLocationScanned(true);
            alloc1.setProductScanned(true);
            alloc1 = allocationRepository.save(alloc1);
            allocationIds.add(alloc1.getId());

            Allocation alloc2 = new Allocation(task, stock2, 5, Status.IN_PROGRESS);
            alloc2.setPickedQuantity(5);
            alloc2.setSourceLocationScanned(true);
            alloc2.setProductScanned(true);
            alloc2 = allocationRepository.save(alloc2);
            allocationIds.add(alloc2.getId());

            return new Long[]{orderLine.getId(), alloc1.getId(), alloc2.getId(), operator.getId(), order.getId()};
        });

        Long orderLineId = ids[0];
        Long alloc1Id = ids[1];
        Long alloc2Id = ids[2];
        Long operatorId = ids[3];
        Long orderId = ids[4];

        // 2. Prepare concurrent threads to complete allocations simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch latch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);

        Runnable pick1 = () -> {
            try {
                barrier.await();
                tx.execute(status -> {
                    Allocation a1 = allocationRepository.findById(alloc1Id).orElseThrow();
                    User op = userRepository.findById(operatorId).orElseThrow();
                    pickingOperatorStrategy.complete(a1, 5, op);
                    return null;
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };

        Runnable pick2 = () -> {
            try {
                barrier.await();
                tx.execute(status -> {
                    Allocation a2 = allocationRepository.findById(alloc2Id).orElseThrow();
                    User op = userRepository.findById(operatorId).orElseThrow();
                    pickingOperatorStrategy.complete(a2, 5, op);
                    return null;
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };

        executor.submit(pick1);
        executor.submit(pick2);

        boolean finishedInTime = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finishedInTime).isTrue();
        assertThat(successCount.get()).isEqualTo(2);

        // 3. Verify concurrency invariant: both picks are serialized by pessimistic write lock,
        // deliveredQuantity is exactly 10 (no lost update from 5 + 5), and shortage is 0.
        OrderLine finalOrderLine = orderLineRepository.findById(orderLineId).orElseThrow();
        assertThat(finalOrderLine.getDeliveredQuantity()).isEqualTo(10);
        assertThat(finalOrderLine.getShortageQuantity()).isEqualTo(0);

        // 4. Verify order status has progressed
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isIn(OrderStatus.IN_PROGRESS, OrderStatus.PARTIALLY_COMPLETED, OrderStatus.COMPLETED);
    }
}
