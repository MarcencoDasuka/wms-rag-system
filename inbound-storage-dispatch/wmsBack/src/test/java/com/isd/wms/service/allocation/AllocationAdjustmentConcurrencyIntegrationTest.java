package com.isd.wms.service.allocation;

import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.*;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.*;
import com.isd.wms.service.AllocationExecutionService;
import com.isd.wms.service.InventoryAdjustmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AllocationAdjustmentConcurrencyIntegrationTest {

    @Autowired
    private AllocationExecutionService allocationExecutionService;

    @Autowired
    private InventoryAdjustmentService inventoryAdjustmentService;

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
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Concurrent allocation completion and inventory reduction prevents ghost picking and preserves data integrity")
    void concurrentAllocationCompletionAndInventoryAdjustment_maintainsIntegrity() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Prepare test fixtures in a dedicated transaction
        Long[] ids = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            User operator = userRepository.findByUsername("operator")
                .orElseThrow(() -> new IllegalStateException("Seed operator not found"));
            User supervisor = userRepository.findByUsername("supervisor")
                .orElseThrow(() -> new IllegalStateException("Seed supervisor not found"));

            Category cat = categoryRepository.save(new Category("CatAdj_" + suffix));
            Product prod = productRepository.save(new Product("ProdAdj_" + suffix, "BAR_ADJ_" + suffix, "Desc Adj", cat));

            Location pickLoc = locationRepository.save(new Location("PLOC_" + suffix, "BC_PLOC_" + suffix, Zone.PICKING, "Pick Loc"));
            Location destLoc = locationRepository.save(new Location("DLOC_" + suffix, "BC_DLOC_" + suffix, Zone.DISPATCH, "Dest Loc"));

            Stock stock = stockRepository.save(new Stock(prod, pickLoc, 10, LocalDate.now(), LocalDate.now().plusYears(1)));
            stock.setReservedQuantity(10);
            stock = stockRepository.save(stock);

            Task task = new Task(supervisor, TaskType.PICKING_ORDER, 10);
            task.setOperator(operator);
            task.setStatus(TaskStatus.IN_PROGRESS);
            task = taskRepository.save(task);

            Order order = orderRepository.save(new Order("ORD-ADJ-" + suffix, destLoc));
            order.setStatus(OrderStatus.IN_PROGRESS);
            order = orderRepository.save(order);

            OrderLine orderLine = new OrderLine(order, task, prod, 10);
            orderLine.setDeliveredQuantity(0);
            orderLine.setShortageQuantity(0);
            orderLine.setStatus(Status.IN_PROGRESS);
            orderLine = orderLineRepository.save(orderLine);

            Allocation alloc = new Allocation(task, stock, 10, Status.IN_PROGRESS);
            alloc.setPickedQuantity(10);
            alloc.setSourceLocationScanned(true);
            alloc.setProductScanned(true);
            alloc = allocationRepository.save(alloc);

            return new Long[]{stock.getId(), alloc.getId(), orderLine.getId(), supervisor.getId(), operator.getId()};
        });

        Long stockId = ids[0];
        Long allocId = ids[1];
        Long orderLineId = ids[2];
        Long supervisorId = ids[3];

        // 2. Prepare 2 concurrent threads: Thread 1 runs completeAllocation, Thread 2 runs adjustStock down to 0
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch latch = new CountDownLatch(2);

        AtomicBoolean completionSucceeded = new AtomicBoolean(false);
        AtomicBoolean adjustmentSucceeded = new AtomicBoolean(false);

        Runnable completionAction = () -> {
            try {
                SecurityContext sc = SecurityContextHolder.createEmptyContext();
                sc.setAuthentication(new UsernamePasswordAuthenticationToken("operator", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
                SecurityContextHolder.setContext(sc);

                barrier.await();
                allocationExecutionService.completeAllocation(allocId);
                completionSucceeded.set(true);
            } catch (InvalidRequestException e) {
                // Expected when adjustment canceled or reduced the allocation
            } catch (Exception e) {
                // Concurrency conflict / aborted
            } finally {
                SecurityContextHolder.clearContext();
                latch.countDown();
            }
        };

        Runnable adjustmentAction = () -> {
            try {
                SecurityContext sc = SecurityContextHolder.createEmptyContext();
                sc.setAuthentication(new UsernamePasswordAuthenticationToken("supervisor", null, List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));
                SecurityContextHolder.setContext(sc);

                barrier.await();
                inventoryAdjustmentService.adjustStock(
                    stockId,
                    new InventoryAdjustmentRequest(0, supervisorId, InventoryAdjustmentReason.DAMAGED, "Damaged stock", null, null)
                );
                adjustmentSucceeded.set(true);
            } catch (ObjectOptimisticLockingFailureException e) {
                // Expected when completion already modified the stock
            } catch (Exception e) {
                // Concurrency conflict / aborted
            } finally {
                SecurityContextHolder.clearContext();
                latch.countDown();
            }
        };

        executor.submit(completionAction);
        executor.submit(adjustmentAction);

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(finished).isTrue();

        // 3. Verify invariants:
        // Invariant 1: No ghost pick. If adjustment succeeded in reducing to 0, completion was rejected and allocation is CANCELED.
        // Invariant 2: Stock quantity never drops below 0.
        // Invariant 3: Order delivered quantity matches physical reality.
        tx.execute(status -> {
            Allocation finalAlloc = allocationRepository.findById(allocId).orElseThrow();
            Stock finalStock = stockRepository.findById(stockId).orElseThrow();
            OrderLine finalOrderLine = orderLineRepository.findById(orderLineId).orElseThrow();

            assertThat(finalStock.getQuantity()).isGreaterThanOrEqualTo(0);

            if (adjustmentSucceeded.get() && !completionSucceeded.get()) {
                // Adjustment won: allocation canceled, no ghost delivery
                assertThat(finalAlloc.getStatus()).isEqualTo(Status.CANCELED);
                assertThat(finalAlloc.getQuantity()).isEqualTo(0);
                assertThat(finalStock.getQuantity()).isEqualTo(0);
                assertThat(finalOrderLine.getDeliveredQuantity()).isEqualTo(0);
            } else if (completionSucceeded.get()) {
                // Completion won: allocation completed, order delivered
                assertThat(finalAlloc.getStatus()).isEqualTo(Status.COMPLETED);
                assertThat(finalOrderLine.getDeliveredQuantity()).isEqualTo(10);
            }

            return null;
        });
    }

    @Test
    @DisplayName("Allocation completion is rejected when remaining quantity was reduced below picked quantity")
    void completeAllocation_whenQuantityReducedBelowPicked_throwsInvalidRequestException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long[] ids = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            User operator = userRepository.findByUsername("operator").orElseThrow();
            User supervisor = userRepository.findByUsername("supervisor").orElseThrow();

            Category cat = categoryRepository.save(new Category("CatAdj2_" + suffix));
            Product prod = productRepository.save(new Product("ProdAdj2_" + suffix, "BAR_ADJ2_" + suffix, "Desc Adj 2", cat));

            Location pickLoc = locationRepository.save(new Location("PLOC2_" + suffix, "BC_PLOC2_" + suffix, Zone.PICKING, "Pick Loc"));
            Location destLoc = locationRepository.save(new Location("DLOC2_" + suffix, "BC_DLOC2_" + suffix, Zone.DISPATCH, "Dest Loc"));

            Stock stock = stockRepository.save(new Stock(prod, pickLoc, 10, LocalDate.now(), LocalDate.now().plusYears(1)));
            stock.setReservedQuantity(4);
            stock = stockRepository.save(stock);

            Task task = new Task(supervisor, TaskType.PICKING_ORDER, 10);
            task.setOperator(operator);
            task.setStatus(TaskStatus.IN_PROGRESS);
            task = taskRepository.save(task);

            Order order = orderRepository.save(new Order("ORD-ADJ2-" + suffix, destLoc));
            order.setStatus(OrderStatus.IN_PROGRESS);
            order = orderRepository.save(order);

            OrderLine orderLine = new OrderLine(order, task, prod, 10);
            orderLine.setDeliveredQuantity(0);
            orderLine.setStatus(Status.IN_PROGRESS);
            orderLine = orderLineRepository.save(orderLine);

            // Allocation was initially for 10, but adjustment reduced its quantity to 4 while pickedQuantity was confirmed as 10
            Allocation alloc = new Allocation(task, stock, 4, Status.IN_PROGRESS);
            alloc.setPickedQuantity(10);
            alloc.setSourceLocationScanned(true);
            alloc.setProductScanned(true);
            alloc = allocationRepository.save(alloc);

            return new Long[]{alloc.getId()};
        });

        Long allocId = ids[0];

        SecurityContext sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(new UsernamePasswordAuthenticationToken("operator", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
        SecurityContextHolder.setContext(sc);
        try {
            assertThatThrownBy(() -> allocationExecutionService.completeAllocation(allocId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot exceed required quantity");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Allocation completion is rejected when allocation was canceled by inventory adjustment")
    void completeAllocation_whenAllocationCanceled_throwsInvalidRequestException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long[] ids = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            User operator = userRepository.findByUsername("operator").orElseThrow();
            User supervisor = userRepository.findByUsername("supervisor").orElseThrow();

            Category cat = categoryRepository.save(new Category("CatAdj3_" + suffix));
            Product prod = productRepository.save(new Product("ProdAdj3_" + suffix, "BAR_ADJ3_" + suffix, "Desc Adj 3", cat));

            Location pickLoc = locationRepository.save(new Location("PLOC3_" + suffix, "BC_PLOC3_" + suffix, Zone.PICKING, "Pick Loc"));
            Location destLoc = locationRepository.save(new Location("DLOC3_" + suffix, "BC_DLOC3_" + suffix, Zone.DISPATCH, "Dest Loc"));

            Stock stock = stockRepository.save(new Stock(prod, pickLoc, 0, LocalDate.now(), LocalDate.now().plusYears(1)));

            Task task = new Task(supervisor, TaskType.PICKING_ORDER, 10);
            task.setOperator(operator);
            task.setStatus(TaskStatus.IN_PROGRESS);
            task = taskRepository.save(task);

            Order order = orderRepository.save(new Order("ORD-ADJ3-" + suffix, destLoc));
            order.setStatus(OrderStatus.IN_PROGRESS);
            order = orderRepository.save(order);

            OrderLine orderLine = new OrderLine(order, task, prod, 10);
            orderLine.setDeliveredQuantity(0);
            orderLine.setStatus(Status.IN_PROGRESS);
            orderLine = orderLineRepository.save(orderLine);

            Allocation alloc = new Allocation(task, stock, 0, Status.CANCELED);
            alloc.setPickedQuantity(0);
            alloc.setSourceLocationScanned(true);
            alloc.setProductScanned(true);
            alloc = allocationRepository.save(alloc);

            return new Long[]{alloc.getId()};
        });

        Long allocId = ids[0];

        SecurityContext sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(new UsernamePasswordAuthenticationToken("operator", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
        SecurityContextHolder.setContext(sc);
        try {
            assertThatThrownBy(() -> allocationExecutionService.completeAllocation(allocId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cancelled");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
