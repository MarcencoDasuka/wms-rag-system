package com.isd.wms.service.allocation;

import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.dto.inventory.RemoveStockRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.*;
import com.isd.wms.exception.InsufficientStockException;
import com.isd.wms.repository.*;
import com.isd.wms.service.InventoryAdjustmentService;
import com.isd.wms.service.InventoryService;
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
class StockLockOrderingConcurrencyIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryAdjustmentService inventoryAdjustmentService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Long> stockIds;
    private List<Long> locationIds;
    private List<Long> productIds;
    private List<Long> categoryIds;

    @BeforeEach
    void setUp() {
        stockIds = new ArrayList<>();
        locationIds = new ArrayList<>();
        productIds = new ArrayList<>();
        categoryIds = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (Long id : stockIds) {
                    jdbcTemplate.update("DELETE FROM stocks WHERE id = ?", id);
                }
                for (Long id : locationIds) {
                    jdbcTemplate.update("DELETE FROM locations WHERE id = ?", id);
                }
                for (Long id : productIds) {
                    jdbcTemplate.update("DELETE FROM products WHERE id = ?", id);
                }
                for (Long id : categoryIds) {
                    jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id);
                }
            });
        } catch (Exception e) {
            System.err.println("Teardown failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("GAP-04: Concurrent removeStock calls serialize via findByIdWithLock preventing overselling and lost updates")
    void concurrentRemoveStock_serializesAndPreventsOverselling() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long[] fixtures = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User operator = userRepository.findByUsername("operator").orElseThrow();

            Category cat = categoryRepository.save(new Category("CatRMS_" + suffix));
            categoryIds.add(cat.getId());
            Product prod = productRepository.save(new Product("ProdRMS_" + suffix, "BAR_RMS_" + suffix, "Desc", cat));
            productIds.add(prod.getId());
            Location loc = locationRepository.save(new Location("PLOC_RMS_" + suffix, "BC_RMS_" + suffix, Zone.PICKING, "Loc"));
            locationIds.add(loc.getId());

            Stock stock = stockRepository.save(new Stock(prod, loc, 10, LocalDate.now(), LocalDate.now().plusYears(1)));
            stockIds.add(stock.getId());
            return new Long[]{stock.getId(), operator.getId()};
        });

        assertThat(fixtures).isNotNull();
        Long stockId = fixtures[0];
        Long operatorId = fixtures[1];

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                SecurityContext sc = SecurityContextHolder.createEmptyContext();
                sc.setAuthentication(new UsernamePasswordAuthenticationToken("operator", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
                SecurityContextHolder.setContext(sc);
                try {
                    startLatch.await();
                    // Each thread attempts to remove 7 units from initial 10 units (total demand 14 > 10)
                    inventoryService.removeStock(new RemoveStockRequest(stockId, 7, operatorId));
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficientCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(errorCount.get()).as("No deadlocks or unexpected errors occurred").isZero();
        assertThat(successCount.get()).as("Exactly one removal should succeed").isEqualTo(1);
        assertThat(insufficientCount.get()).as("Second removal cleanly gets InsufficientStockException").isEqualTo(1);

        tx.executeWithoutResult(status -> {
            Stock stock = stockRepository.findById(stockId).orElseThrow();
            assertThat(stock.getQuantity()).isEqualTo(3);
            assertThat(stock.getReservedQuantity()).isEqualTo(0);
            assertThat(stock.getAvailableQuantity()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("GAP-04: Deterministic lock ordering across multiple stocks prevents deadlocks in concurrent adjustments")
    void concurrentMultiStockOperations_orderedAscending_doesNotDeadlock() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long[] fixtures = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User supervisor = userRepository.findByUsername("supervisor").orElseThrow();

            Category cat = categoryRepository.save(new Category("CatORD_" + suffix));
            categoryIds.add(cat.getId());
            Product prod = productRepository.save(new Product("ProdORD_" + suffix, "BAR_ORD_" + suffix, "Desc", cat));
            productIds.add(prod.getId());
            Location loc1 = locationRepository.save(new Location("PLOC_ORD1_" + suffix, "BC_ORD1_" + suffix, Zone.PICKING, "Loc 1"));
            locationIds.add(loc1.getId());
            Location loc2 = locationRepository.save(new Location("PLOC_ORD2_" + suffix, "BC_ORD2_" + suffix, Zone.PICKING, "Loc 2"));
            locationIds.add(loc2.getId());

            Stock stock1 = stockRepository.save(new Stock(prod, loc1, 20, LocalDate.now(), LocalDate.now().plusYears(1)));
            stockIds.add(stock1.getId());
            Stock stock2 = stockRepository.save(new Stock(prod, loc2, 20, LocalDate.now(), LocalDate.now().plusYears(1)));
            stockIds.add(stock2.getId());

            // Ensure stock1 has lower ID than stock2
            Long lowId = Math.min(stock1.getId(), stock2.getId());
            Long highId = Math.max(stock1.getId(), stock2.getId());

            return new Long[]{lowId, highId, supervisor.getId()};
        });

        assertThat(fixtures).isNotNull();
        Long lowId = fixtures[0];
        Long highId = fixtures[1];
        Long supervisorId = fixtures[2];

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Thread 1 locks and adjusts stock with high ID first in its request
        executor.submit(() -> {
            SecurityContext sc = SecurityContextHolder.createEmptyContext();
            sc.setAuthentication(new UsernamePasswordAuthenticationToken("supervisor", null, List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));
            SecurityContextHolder.setContext(sc);
            try {
                startLatch.await();
                inventoryAdjustmentService.adjustStock(highId, new InventoryAdjustmentRequest(
                    15, supervisorId, InventoryAdjustmentReason.DAMAGED, "Adjust high ID stock", null, null
                ));
                successCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
                doneLatch.countDown();
            }
        });

        // Thread 2 locks and adjusts stock with low ID
        executor.submit(() -> {
            SecurityContext sc = SecurityContextHolder.createEmptyContext();
            sc.setAuthentication(new UsernamePasswordAuthenticationToken("supervisor", null, List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));
            SecurityContextHolder.setContext(sc);
            try {
                startLatch.await();
                inventoryAdjustmentService.adjustStock(lowId, new InventoryAdjustmentRequest(
                    15, supervisorId, InventoryAdjustmentReason.DAMAGED, "Adjust low ID stock", null, null
                ));
                successCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(errorCount.get()).as("Both adjustments complete without deadlocks").isZero();
        assertThat(successCount.get()).as("Both adjustments succeeded").isEqualTo(2);

        tx.executeWithoutResult(status -> {
            Stock stockLow = stockRepository.findById(lowId).orElseThrow();
            Stock stockHigh = stockRepository.findById(highId).orElseThrow();
            assertThat(stockLow.getQuantity()).isEqualTo(15);
            assertThat(stockHigh.getQuantity()).isEqualTo(15);
        });
    }
}
