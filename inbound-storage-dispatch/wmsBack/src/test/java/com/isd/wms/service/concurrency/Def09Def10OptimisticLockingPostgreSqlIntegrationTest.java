package com.isd.wms.service.concurrency;

import com.isd.wms.entity.Location;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.Zone;
import com.isd.wms.exception.ApiErrorResponse;
import com.isd.wms.exception.GlobalExceptionHandler;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authentic multi-threaded concurrency integration test against live PostgreSQL (remediating DEF-09 and DEF-10).
 *
 * <p>Validates real Hibernate {@code @Version} optimistic locking execution:
 * 1. Two concurrent physical database transactions read the same persistent entity at version = 0.
 * 2. Transaction 1 modifies the entity and successfully commits (incrementing DB version to 1).
 * 3. Transaction 2 attempts to flush its stale update (expecting version = 0 in PostgreSQL),
 *    triggering a real {@link ObjectOptimisticLockingFailureException} from Hibernate.
 * 4. Zero database pollution invariant: all created entities are strictly tracked and deleted in {@code @AfterEach}.</p>
 */
@SpringBootTest
class Def09Def10OptimisticLockingPostgreSqlIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReplenishmentRepository replenishmentRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdOrderIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdReplenishmentIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdLocationIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdProductIds = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        // Enforce strict cleanup of all test-generated entities in reverse dependency order
        for (Long orderId : createdOrderIds) {
            jdbcTemplate.update("DELETE FROM order_lines WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        }
        for (Long replId : createdReplenishmentIds) {
            jdbcTemplate.update("DELETE FROM replenishments WHERE id = ?", replId);
        }
        for (Long locId : createdLocationIds) {
            jdbcTemplate.update("DELETE FROM locations WHERE id = ?", locId);
        }
        for (Long prodId : createdProductIds) {
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", prodId);
        }
        createdOrderIds.clear();
        createdReplenishmentIds.clear();
        createdLocationIds.clear();
        createdProductIds.clear();
    }

    private Location createTestLocation() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Location location = new Location("LOC-CONC-" + uid, "BC-CONC-" + uid, Zone.PICKING, "Concurrency Test Location");
        Location saved = locationRepository.save(location);
        createdLocationIds.add(saved.getId());
        return saved;
    }

    private Product createTestProduct() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Product product = new Product();
        product.setName("Product-Conc-" + uid);
        product.setBarcode("PROD-BC-" + uid);
        product.setAutoReplenish(false);
        Product saved = productRepository.save(product);
        createdProductIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("DEF-09: Concurrent Order status updates in PostgreSQL trigger genuine Hibernate ObjectOptimisticLockingFailureException")
    void concurrentOrderUpdate_triggersPostgreSqlOptimisticLockingFailure() throws Exception {
        Location location = createTestLocation();

        // 1. Create and commit initial Order with version = 0
        TransactionTemplate initTx = new TransactionTemplate(transactionManager);
        initTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long orderId = initTx.execute(status -> {
            String uid = UUID.randomUUID().toString().substring(0, 8);
            Order order = new Order("ORD-CONC-" + uid, location, "supervisor_conc_test");
            order.setStatus(OrderStatus.CREATED);
            Order saved = orderRepository.save(order);
            return saved.getId();
        });
        assertThat(orderId).isNotNull();
        createdOrderIds.add(orderId);

        // Verify initial state in PostgreSQL
        Order initial = orderRepository.findById(orderId).orElseThrow();
        assertThat(initial.getVersion()).isEqualTo(0L);
        assertThat(initial.getStatus()).isEqualTo(OrderStatus.CREATED);

        CountDownLatch bothReadLatch = new CountDownLatch(2);
        CountDownLatch tx1CommittedLatch = new CountDownLatch(1);
        AtomicReference<Throwable> tx2Failure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Thread 1: Read order (version = 0), wait for thread 2 to also read, then update to PICKING and commit
            Future<?> future1 = executor.submit(() -> {
                TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
                tx1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx1.execute(status -> {
                    Order o1 = orderRepository.findById(orderId).orElseThrow();
                    assertThat(o1.getVersion()).isEqualTo(0L);

                    bothReadLatch.countDown();
                    try {
                        bothReadLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    o1.setStatus(OrderStatus.IN_PROGRESS);
                    orderRepository.saveAndFlush(o1);
                    return null;
                });
                tx1CommittedLatch.countDown();
            });

            // Thread 2: Read order (version = 0), wait for Thread 1 to commit (which sets DB version = 1),
            // then attempt to save stale order -> MUST trigger ObjectOptimisticLockingFailureException from PostgreSQL
            Future<?> future2 = executor.submit(() -> {
                TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
                tx2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                try {
                    tx2.execute(status -> {
                        Order o2 = orderRepository.findById(orderId).orElseThrow();
                        assertThat(o2.getVersion()).isEqualTo(0L);

                        bothReadLatch.countDown();
                        try {
                            bothReadLatch.await(5, TimeUnit.SECONDS);
                            tx1CommittedLatch.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        o2.setStatus(OrderStatus.CANCELED);
                        orderRepository.saveAndFlush(o2);
                        return null;
                    });
                } catch (Throwable t) {
                    tx2Failure.set(t);
                }
            });

            future1.get(10, TimeUnit.SECONDS);
            future2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Assert that Transaction 2 failed with genuine Optimistic Locking failure
        assertThat(tx2Failure.get())
                .isNotNull()
                .satisfies(t -> assertThat(
                        t instanceof ObjectOptimisticLockingFailureException ||
                        t instanceof OptimisticLockException ||
                        t.getCause() instanceof ObjectOptimisticLockingFailureException ||
                        t.getCause() instanceof OptimisticLockException
                ).isTrue());

        // Assert that in PostgreSQL, Transaction 1 won: version = 1, status = IN_PROGRESS
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getVersion()).isEqualTo(1L);
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("DEF-10: Concurrent Replenishment updates in PostgreSQL trigger genuine Hibernate ObjectOptimisticLockingFailureException")
    void concurrentReplenishmentUpdate_triggersPostgreSqlOptimisticLockingFailure() throws Exception {
        Location location = createTestLocation();
        Product product = createTestProduct();

        // 1. Create and commit initial Replenishment with version = 0
        TransactionTemplate initTx = new TransactionTemplate(transactionManager);
        initTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long replId = initTx.execute(status -> {
            Replenishment repl = new Replenishment(product, 25, location, "supervisor_conc_test");
            repl.setStatus(Status.CREATED);
            Replenishment saved = replenishmentRepository.save(repl);
            return saved.getId();
        });
        assertThat(replId).isNotNull();
        createdReplenishmentIds.add(replId);

        // Verify initial state in PostgreSQL
        Replenishment initial = replenishmentRepository.findById(replId).orElseThrow();
        assertThat(initial.getVersion()).isEqualTo(0L);
        assertThat(initial.getStatus()).isEqualTo(Status.CREATED);

        CountDownLatch bothReadLatch = new CountDownLatch(2);
        CountDownLatch tx1CommittedLatch = new CountDownLatch(1);
        AtomicReference<Throwable> tx2Failure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Thread 1: Read replenishment (version = 0), update status to IN_PROGRESS and commit
            Future<?> future1 = executor.submit(() -> {
                TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
                tx1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx1.execute(status -> {
                    Replenishment r1 = replenishmentRepository.findById(replId).orElseThrow();
                    assertThat(r1.getVersion()).isEqualTo(0L);

                    bothReadLatch.countDown();
                    try {
                        bothReadLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    r1.setStatus(Status.IN_PROGRESS);
                    replenishmentRepository.saveAndFlush(r1);
                    return null;
                });
                tx1CommittedLatch.countDown();
            });

            // Thread 2: Read replenishment (version = 0), wait for Thread 1 to commit, attempt to update to CANCELLED
            Future<?> future2 = executor.submit(() -> {
                TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
                tx2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                try {
                    tx2.execute(status -> {
                        Replenishment r2 = replenishmentRepository.findById(replId).orElseThrow();
                        assertThat(r2.getVersion()).isEqualTo(0L);

                        bothReadLatch.countDown();
                        try {
                            bothReadLatch.await(5, TimeUnit.SECONDS);
                            tx1CommittedLatch.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        r2.setStatus(Status.CANCELED);
                        replenishmentRepository.saveAndFlush(r2);
                        return null;
                    });
                } catch (Throwable t) {
                    tx2Failure.set(t);
                }
            });

            future1.get(10, TimeUnit.SECONDS);
            future2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Assert that Transaction 2 failed with genuine Optimistic Locking failure
        assertThat(tx2Failure.get())
                .isNotNull()
                .satisfies(t -> assertThat(
                        t instanceof ObjectOptimisticLockingFailureException ||
                        t instanceof OptimisticLockException ||
                        t.getCause() instanceof ObjectOptimisticLockingFailureException ||
                        t.getCause() instanceof OptimisticLockException
                ).isTrue());

        // Assert that in PostgreSQL, Transaction 1 won: version = 1, status = IN_PROGRESS
        Replenishment finalRepl = replenishmentRepository.findById(replId).orElseThrow();
        assertThat(finalRepl.getVersion()).isEqualTo(1L);
        assertThat(finalRepl.getStatus()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    @DisplayName("GlobalExceptionHandler: ObjectOptimisticLockingFailureException translates to HTTP 409 Conflict")
    void optimisticLockingException_isHandledAsHttp409ConflictByGlobalExceptionHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException(Order.class, 999L);

        ResponseEntity<ApiErrorResponse> response = handler.handleOptimisticLocking(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).contains("modified by another transaction");
    }
}
