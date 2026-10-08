package com.isd.wms.service;

import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.ApiErrorResponse;
import com.isd.wms.exception.GlobalExceptionHandler;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.ReplenishmentMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.validation.SecurityFacade;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def10ReplenishmentAssignmentConcurrencyRemediationTest {

    private ReplenishmentRepository replenishmentRepository;
    private TaskRepository taskRepository;
    private TaskService taskService;
    private SecurityFacade securityFacade;
    private ReplenishmentService replenishmentService;
    private GlobalExceptionHandler exceptionHandler;

    private Replenishment persistentReplenishment;
    private final AtomicLong databaseVersion = new AtomicLong(0L);
    private final List<Task> committedTasks = new CopyOnWriteArrayList<>();
    private final AtomicInteger assignedTaskCount = new AtomicInteger(0);

    private final AtomicBoolean enableConcurrencyBarrier = new AtomicBoolean(false);
    private final CountDownLatch bothReadLatch = new CountDownLatch(2);
    private final CountDownLatch proceedToSaveLatch = new CountDownLatch(1);

    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        committedTasks.clear();
        assignedTaskCount.set(0);
        databaseVersion.set(0L);
        enableConcurrencyBarrier.set(false);
        exceptionHandler = new GlobalExceptionHandler();

        Product product = new Product("Bulk Part", "BP-01", null, null);
        ReflectionTestUtils.setField(product, "id", 10L);

        Location dest = new Location("Pick Face", "PICK-01", null, null, true);
        ReflectionTestUtils.setField(dest, "id", 20L);

        persistentReplenishment = new Replenishment(product, 50, dest, "supervisor_carol");
        ReflectionTestUtils.setField(persistentReplenishment, "id", 200L);
        ReflectionTestUtils.setField(persistentReplenishment, "version", 0L);
        persistentReplenishment.setStatus(Status.CREATED);

        replenishmentRepository = createProxy(ReplenishmentRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findById".equals(name)) {
                Long id = (Long) args[0];
                if (Long.valueOf(200L).equals(id)) {
                    Replenishment copy = new Replenishment(
                        persistentReplenishment.getProduct(),
                        persistentReplenishment.getRequestedQuantity(),
                        persistentReplenishment.getDestinationLocation(),
                        persistentReplenishment.getCreatedBy()
                    );
                    ReflectionTestUtils.setField(copy, "id", persistentReplenishment.getId());
                    ReflectionTestUtils.setField(copy, "version", databaseVersion.get());
                    copy.setStatus(persistentReplenishment.getStatus());
                    copy.setLogicId(persistentReplenishment.getLogicId());
                    persistentReplenishment.getTask().ifPresent(copy::setTask);

                    if (enableConcurrencyBarrier.get()) {
                        bothReadLatch.countDown();
                        try {
                            proceedToSaveLatch.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    return Optional.of(copy);
                }
                return Optional.empty();
            }
            if ("saveAndFlush".equals(name)) {
                Replenishment target = (Replenishment) args[0];
                synchronized (databaseVersion) {
                    Long currentEntityVersion = (Long) ReflectionTestUtils.getField(target, "version");
                    if (!Objects.equals(currentEntityVersion, databaseVersion.get())) {
                        throw new ObjectOptimisticLockingFailureException(Replenishment.class, target.getId());
                    }
                    long nextVersion = databaseVersion.incrementAndGet();
                    persistentReplenishment.setStatus(target.getStatus());
                    ReflectionTestUtils.setField(persistentReplenishment, "version", nextVersion);
                    ReflectionTestUtils.setField(target, "version", nextVersion);
                    return target;
                }
            }
            if ("save".equals(name)) {
                Replenishment target = (Replenishment) args[0];
                synchronized (databaseVersion) {
                    target.getTask().ifPresent(t -> {
                        persistentReplenishment.setTask(t);
                        committedTasks.add(t);
                    });
                    return target;
                }
            }
            if ("findByTaskId".equals(name)) {
                return Optional.of(persistentReplenishment);
            }
            return null;
        });

        taskRepository = createProxy(TaskRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findById".equals(name)) {
                Task t = new Task();
                ReflectionTestUtils.setField(t, "id", args[0]);
                return Optional.of(t);
            }
            if ("save".equals(name)) {
                return args[0];
            }
            return null;
        });

        UserRepository userRepository = createProxy(UserRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                com.isd.wms.entity.User u = new com.isd.wms.entity.User();
                ReflectionTestUtils.setField(u, "id", args[0]);
                return Optional.of(u);
            }
            return Optional.empty();
        });

        AllocationRepository allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> {
            if ("findAllByTaskId".equals(method.getName())) {
                return Collections.emptyList();
            }
            if ("saveAll".equals(method.getName())) {
                return args[0];
            }
            return null;
        });

        securityFacade = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role roleName) {
                return false;
            }

            @Override
            public String getCurrentUsername() {
                return "supervisor_carol";
            }
        };

        taskService = new TaskService(taskRepository, userRepository, allocationRepository, replenishmentRepository, securityFacade, null) {
            @Override
            public Task createTask(TaskType type, Integer requestedQuantity, Long productId) {
                Task t = new Task(null, type, requestedQuantity);
                ReflectionTestUtils.setField(t, "id", System.nanoTime());
                return t;
            }

            @Override
            public void assignTask(Long taskId, Long operatorId) {
                assignedTaskCount.incrementAndGet();
            }
        };

        TransportUnitRepository transportUnitRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> Optional.empty());
        LocationRepository locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> Optional.empty());
        ProductRepository productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> Optional.empty());
        StockRepository stockRepository = createProxy(StockRepository.class, (proxy, method, args) -> Optional.empty());
        ReplenishmentMapper replenishmentMapper = new ReplenishmentMapper(transportUnitRepository);
        ImportService importService = null;

        replenishmentService = new ReplenishmentService(
            replenishmentRepository,
            stockRepository,
            productRepository,
            locationRepository,
            allocationRepository,
            transportUnitRepository,
            replenishmentMapper,
            null, // workflowService
            taskService,
            importService,
            securityFacade
        );
    }

    @Test
    @DisplayName("DEF-10 Concurrency Proof: Concurrent assignReplenishment against same version yields 1 winner, 1 conflict, and prevents duplicate task")
    void concurrentReplenishmentAssignment_producesSingleWinnerAndRollsBackLosingSideEffects() throws Exception {
        enableConcurrencyBarrier.set(true);
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            final long operatorId = 30L + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    replenishmentService.assignReplenishment(200L, operatorId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e);
                    if (e instanceof ObjectOptimisticLockingFailureException || e instanceof OptimisticLockException) {
                        conflictCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        bothReadLatch.await(5, TimeUnit.SECONDS);
        proceedToSaveLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // INVARIANT 1: Exactly one assignment succeeds
        assertThat(successCount.get())
            .as("Exactly one assignment operation must succeed")
            .isEqualTo(1);

        // INVARIANT 2: Exactly one conflict via Optimistic Locking
        assertThat(conflictCount.get())
            .as("The losing concurrent assignment must throw an optimistic locking conflict")
            .isEqualTo(1);

        // INVARIANT 3: Exactly one committed task exists for the replenishment
        assertThat(committedTasks)
            .as("The resulting database state must contain at most one successfully linked task")
            .hasSize(1);

        // INVARIANT 4: Final replenishment status is ASSIGNED and version incremented
        assertThat(persistentReplenishment.getStatus())
            .as("Replenishment status must be ASSIGNED")
            .isEqualTo(Status.ASSIGNED);

        assertThat(databaseVersion.get())
            .as("Persistent replenishment version must be incremented")
            .isEqualTo(1L);

        assertThat(assignedTaskCount.get())
            .as("Task assignment to operator must only occur once")
            .isEqualTo(1);

        // INVARIANT 5: Conflict exception produces HTTP 409 via GlobalExceptionHandler
        Exception losingException = errors.stream()
            .filter(e -> e instanceof ObjectOptimisticLockingFailureException || e instanceof OptimisticLockException)
            .findFirst()
            .orElseThrow();

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleOptimisticLocking(losingException);
        assertThat(response.getStatusCode())
            .as("OptimisticLockingFailure must map to HTTP 409 Conflict")
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
            .contains("Concurrent modification conflict");
    }

    @Test
    @DisplayName("DEF-10 Invariant Proof: Re-assignment of already ASSIGNED replenishment is rejected before creating task")
    void assignReplenishment_whenAlreadyAssigned_isRejectedWithoutSideEffects() {
        persistentReplenishment.setStatus(Status.ASSIGNED);
        ReflectionTestUtils.setField(persistentReplenishment, "version", 1L);
        databaseVersion.set(1L);

        assertThatThrownBy(() -> replenishmentService.assignReplenishment(200L, 50L))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("only allowed for CREATED replenishments");

        assertThat(committedTasks)
            .as("No new tasks must be committed when replenishment is already assigned")
            .isEmpty();
    }
}
