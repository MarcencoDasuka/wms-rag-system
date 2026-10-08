package com.isd.wms.service.allocation;

import com.isd.wms.entity.*;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskStatus;
import com.isd.wms.enums.TaskType;
import com.isd.wms.enums.Zone;
import com.isd.wms.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class LocationProductExclusivityConcurrencyIntegrationTest {

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ReplenishmentRepository replenishmentRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReplenishmentOperatorStrategy replenishmentOperatorStrategy;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Long> allocationIds;
    private List<Long> stockIds;
    private List<Long> replenishmentIds;
    private List<Long> taskIds;
    private List<Long> locationIds;
    private List<Long> productIds;
    private List<Long> categoryIds;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_stocks_active_location ON stocks (location_id) WHERE available = true;");
        allocationIds = new ArrayList<>();
        stockIds = new ArrayList<>();
        replenishmentIds = new ArrayList<>();
        taskIds = new ArrayList<>();
        locationIds = new ArrayList<>();
        productIds = new ArrayList<>();
        categoryIds = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                allocationIds.forEach(id -> jdbcTemplate.update("DELETE FROM allocations WHERE id = ?", id));
                replenishmentIds.forEach(id -> jdbcTemplate.update("DELETE FROM replenishments WHERE id = ?", id));
                stockIds.forEach(id -> jdbcTemplate.update("DELETE FROM stocks WHERE id = ?", id));
                taskIds.forEach(id -> jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", id));
                locationIds.forEach(id -> jdbcTemplate.update("DELETE FROM locations WHERE id = ?", id));
                productIds.forEach(id -> jdbcTemplate.update("DELETE FROM products WHERE id = ?", id));
                categoryIds.forEach(id -> jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id));
                return null;
            });
        } catch (Exception e) {
            System.err.println("Teardown failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Concurrent replenishment dispatches of different products to the same location enforce exclusivity and prevent duplicate stock")
    void concurrentReplenishment_differentProductsToSameLocation_enforcesLocationExclusivity() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Prepare test fixtures in a dedicated transaction
        Long[] ids = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            User operator = userRepository.findByUsername("operator")
                .orElseThrow(() -> new IllegalStateException("Seed operator not found"));
            User supervisor = userRepository.findByUsername("supervisor")
                .orElseThrow(() -> new IllegalStateException("Seed supervisor not found"));

            Category cat = categoryRepository.save(new Category("CatEx_" + suffix));
            categoryIds.add(cat.getId());
            Product prodA = productRepository.save(new Product("ProdA_" + suffix, "BARA_" + suffix, "Desc A", cat));
            productIds.add(prodA.getId());
            Product prodB = productRepository.save(new Product("ProdB_" + suffix, "BARB_" + suffix, "Desc B", cat));
            productIds.add(prodB.getId());

            Location destLoc = locationRepository.save(new Location("DST_" + suffix, "BC_DST_" + suffix, Zone.PICKING, "Pick Face Dest"));
            locationIds.add(destLoc.getId());
            Location srcLocA = locationRepository.save(new Location("SRCA_" + suffix, "BC_SRCA_" + suffix, Zone.REPLENISHMENT, "Reserve A"));
            locationIds.add(srcLocA.getId());
            Location srcLocB = locationRepository.save(new Location("SRCB_" + suffix, "BC_SRCB_" + suffix, Zone.REPLENISHMENT, "Reserve B"));
            locationIds.add(srcLocB.getId());

            Stock stockA = stockRepository.save(new Stock(prodA, srcLocA, 10, LocalDate.now(), LocalDate.now().plusYears(1)));
            stockA.setReservedQuantity(10);
            stockA = stockRepository.save(stockA);
            stockIds.add(stockA.getId());

            Stock stockB = stockRepository.save(new Stock(prodB, srcLocB, 10, LocalDate.now(), LocalDate.now().plusYears(1)));
            stockB.setReservedQuantity(10);
            stockB = stockRepository.save(stockB);
            stockIds.add(stockB.getId());

            // Replenishment A for Product A to destLoc
            Task taskA = new Task(supervisor, TaskType.REPLENISHMENT, 10);
            taskA.setOperator(operator);
            taskA.setStatus(TaskStatus.IN_PROGRESS);
            taskA = taskRepository.save(taskA);
            taskIds.add(taskA.getId());

            Replenishment replA = new Replenishment(prodA, 10, destLoc);
            replA.setTask(taskA);
            replA.setStatus(Status.IN_PROGRESS);
            replA.setLogicId("RPLA-" + suffix.toUpperCase());
            replA = replenishmentRepository.save(replA);
            replenishmentIds.add(replA.getId());

            Allocation allocA = new Allocation(taskA, stockA, 10, Status.COMPLETED);
            allocA.setPickedQuantity(10);
            allocA.setSourceLocationScanned(true);
            allocA.setProductScanned(true);
            allocA = allocationRepository.save(allocA);
            allocationIds.add(allocA.getId());

            // Replenishment B for Product B to destLoc
            Task taskB = new Task(supervisor, TaskType.REPLENISHMENT, 10);
            taskB.setOperator(operator);
            taskB.setStatus(TaskStatus.IN_PROGRESS);
            taskB = taskRepository.save(taskB);
            taskIds.add(taskB.getId());

            Replenishment replB = new Replenishment(prodB, 10, destLoc);
            replB.setTask(taskB);
            replB.setStatus(Status.IN_PROGRESS);
            replB.setLogicId("RPLB-" + suffix.toUpperCase());
            replB = replenishmentRepository.save(replB);
            replenishmentIds.add(replB.getId());

            Allocation allocB = new Allocation(taskB, stockB, 10, Status.COMPLETED);
            allocB.setPickedQuantity(10);
            allocB.setSourceLocationScanned(true);
            allocB.setProductScanned(true);
            allocB = allocationRepository.save(allocB);
            allocationIds.add(allocB.getId());

            return new Long[]{destLoc.getId(), allocA.getId(), allocB.getId(), prodA.getId(), prodB.getId()};
        });

        Long destLocId = ids[0];
        Long allocAId = ids[1];
        Long allocBId = ids[2];
        Long prodAId = ids[3];
        Long prodBId = ids[4];

        // 2. Execute concurrent dispatches simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch latch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Runnable dispatchA = () -> {
            try {
                barrier.await();
                tx.execute(status -> {
                    Allocation a = allocationRepository.findById(allocAId).orElseThrow();
                    replenishmentOperatorStrategy.dispatch(a, "TU-A");
                    return null;
                });
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("occupied by a different product")) {
                    conflictCount.incrementAndGet();
                } else {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("occupied by a different product")) {
                    conflictCount.incrementAndGet();
                } else {
                    e.printStackTrace();
                }
            } finally {
                latch.countDown();
            }
        };

        Runnable dispatchB = () -> {
            try {
                barrier.await();
                tx.execute(status -> {
                    Allocation b = allocationRepository.findById(allocBId).orElseThrow();
                    replenishmentOperatorStrategy.dispatch(b, "TU-B");
                    return null;
                });
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("occupied by a different product")) {
                    conflictCount.incrementAndGet();
                } else {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("occupied by a different product")) {
                    conflictCount.incrementAndGet();
                } else {
                    e.printStackTrace();
                }
            } finally {
                latch.countDown();
            }
        };

        executor.submit(dispatchA);
        executor.submit(dispatchB);

        boolean finishedInTime = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finishedInTime).isTrue();

        // 3. Verify that exactly one operation succeeded and the other received a controlled conflict
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // 4. Invariant check: In the database, destination location has exactly ONE active stock
        List<Stock> activeStocks = stockRepository.findAllByAvailableIsTrue().stream()
            .filter(s -> s.getLocation().getId().equals(destLocId))
            .toList();

        assertThat(activeStocks).hasSize(1);
        Stock winnerStock = activeStocks.getFirst();
        assertThat(winnerStock.getQuantity()).isEqualTo(10);
        assertThat(winnerStock.getProduct().orElseThrow().getId()).isIn(prodAId, prodBId);
    }
}
