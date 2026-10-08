package com.isd.wms.service;

import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.ApiErrorResponse;
import com.isd.wms.exception.GlobalExceptionHandler;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.OrderMapper;
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

class Def09OrderAssignmentConcurrencyRemediationTest {

    private OrderRepository orderRepository;
    private OrderLineRepository orderLineRepository;
    private TaskRepository taskRepository;
    private AllocationRepository allocationRepository;
    private TransportUnitRepository transportUnitRepository;
    private LocationRepository locationRepository;
    private TaskService taskService;
    private SecurityFacade securityFacade;
    private OrderService orderService;
    private GlobalExceptionHandler exceptionHandler;

    private Order persistentOrder;
    private final AtomicLong databaseVersion = new AtomicLong(0L);
    private final List<Task> createdTasks = new CopyOnWriteArrayList<>();
    private final AtomicInteger cascadeUpdateCount = new AtomicInteger(0);

    private final AtomicBoolean enableConcurrencyBarrier = new AtomicBoolean(false);
    private final CountDownLatch bothReadLatch = new CountDownLatch(2);
    private final CountDownLatch proceedToSaveLatch = new CountDownLatch(1);

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        createdTasks.clear();
        cascadeUpdateCount.set(0);
        databaseVersion.set(0L);
        enableConcurrencyBarrier.set(false);
        exceptionHandler = new GlobalExceptionHandler();

        persistentOrder = new Order("LOGIC-ORD-100");
        ReflectionTestUtils.setField(persistentOrder, "id", 100L);
        ReflectionTestUtils.setField(persistentOrder, "version", 0L);
        persistentOrder.setStatus(OrderStatus.CREATED);
        persistentOrder.setCreatedBy("supervisor_bob");

        Product product = new Product("Widget", "WGT-01", null, null);
        ReflectionTestUtils.setField(product, "id", 1L);

        OrderLine line = new OrderLine(persistentOrder, product, 5);
        ReflectionTestUtils.setField(line, "id", 201L);
        persistentOrder.getOrderLines().add(line);

        // Simulated thread-safe OrderRepository with atomic Optimistic Locking semantics
        orderRepository = createProxy(OrderRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findById".equals(name)) {
                Long id = (Long) args[0];
                if (Long.valueOf(100L).equals(id)) {
                    Order copy = new Order(persistentOrder.getLogicId());
                    ReflectionTestUtils.setField(copy, "id", persistentOrder.getId());
                    ReflectionTestUtils.setField(copy, "version", databaseVersion.get());
                    copy.setStatus(persistentOrder.getStatus());
                    copy.setCreatedBy(persistentOrder.getCreatedBy());
                    for (OrderLine ol : persistentOrder.getOrderLines()) {
                        OrderLine lineCopy = new OrderLine(copy, ol.getProduct(), ol.getRequestedQuantity());
                        ReflectionTestUtils.setField(lineCopy, "id", ol.getId());
                        copy.getOrderLines().add(lineCopy);
                    }

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
            if ("saveAndFlush".equals(name) || "save".equals(name)) {
                Order target = (Order) args[0];
                synchronized (databaseVersion) {
                    Long currentEntityVersion = (Long) ReflectionTestUtils.getField(target, "version");
                    if (!Objects.equals(currentEntityVersion, databaseVersion.get())) {
                        throw new ObjectOptimisticLockingFailureException(Order.class, target.getId());
                    }
                    // Optimistic lock succeeds: advance version and update persistent order
                    long nextVersion = databaseVersion.incrementAndGet();
                    persistentOrder.setStatus(target.getStatus());
                    ReflectionTestUtils.setField(persistentOrder, "version", nextVersion);
                    ReflectionTestUtils.setField(target, "version", nextVersion);
                    return target;
                }
            }
            if ("findSupervisorUsernamesByOrder".equals(name)) {
                return List.of("supervisor_bob");
            }
            if ("findOperatorIdByOrderId".equals(name)) {
                return Optional.empty();
            }
            return null;
        });

        taskService = new TaskService(taskRepository, null, allocationRepository, null, securityFacade, null) {
            @Override
            public Task createTask(TaskType type, Integer requestedQuantity, Long productId) {
                Task t = new Task(null, type, requestedQuantity);
                ReflectionTestUtils.setField(t, "id", System.nanoTime());
                createdTasks.add(t);
                return t;
            }
        };

        orderLineRepository = createProxy(OrderLineRepository.class, (proxy, method, args) -> {
            if ("saveAllAndFlush".equals(method.getName())) {
                return args[0];
            }
            if ("updateStatusByOrderId".equals(method.getName())) {
                return 1;
            }
            return Collections.emptyList();
        });

        taskRepository = createProxy(TaskRepository.class, (proxy, method, args) -> {
            if ("updateOperatorByOrderId".equals(method.getName())) {
                cascadeUpdateCount.incrementAndGet();
                return 1;
            }
            return null;
        });

        allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> {
            if ("updateStatusByOrderId".equals(method.getName())) {
                return 1;
            }
            return Collections.emptyList();
        });

        transportUnitRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> Optional.empty());
        locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> Optional.empty());

        securityFacade = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role roleName) {
                return false;
            }

            @Override
            public String getCurrentUsername() {
                return "supervisor_bob";
            }

            @Override
            public User getCurrentUser() {
                return null;
            }
        };

        OrderMapper orderMapper = new OrderMapper(transportUnitRepository);
        ImportService importService = null;

        orderService = new OrderService(
            null, // extendedOrderMapper
            orderMapper,
            orderRepository,
            locationRepository,
            null, // orderLineService
            allocationRepository,
            taskRepository,
            orderLineRepository,
            importService,
            securityFacade,
            taskService,
            transportUnitRepository
        );
    }

    @Test
    @DisplayName("DEF-09 Concurrency Proof: Concurrent assignOrder against same entity version yields exactly 1 success and 1 conflict")
    void concurrentOrderAssignment_againstSameVersion_producesExactlyOneWinnerAndOneConflict() throws Exception {
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
            final long operatorId = 10L + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    orderService.assignOrder(100L, operatorId);
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

        // INVARIANT 2: Exactly one operation conflicts via Optimistic Locking
        assertThat(conflictCount.get())
            .as("The competing operation must fail with optimistic lock conflict")
            .isEqualTo(1);

        // INVARIANT 3: Database state contains updated status and incremented version
        assertThat(persistentOrder.getStatus())
            .as("Order status must be updated to ASSIGNED")
            .isEqualTo(OrderStatus.ASSIGNED);

        assertThat(databaseVersion.get())
            .as("Entity version must be incremented from 0 to 1")
            .isEqualTo(1L);

        // INVARIANT 4: Cascade updates executed exactly once
        assertThat(cascadeUpdateCount.get())
            .as("Task operator assignment cascade must be executed exactly once")
            .isEqualTo(1);

        // INVARIANT 5: Conflict maps to HTTP 409 Conflict in GlobalExceptionHandler
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
    @DisplayName("DEF-09 Invariant Proof: Re-assignment of already ASSIGNED order is rejected before creating tasks")
    void assignOrder_whenAlreadyAssigned_isRejectedWithoutSideEffects() {
        persistentOrder.setStatus(OrderStatus.ASSIGNED);
        ReflectionTestUtils.setField(persistentOrder, "version", 1L);
        databaseVersion.set(1L);

        assertThatThrownBy(() -> orderService.assignOrder(100L, 20L))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Order assignment is not allowed");

        assertThat(createdTasks)
            .as("No tasks must be generated when order assignment is rejected")
            .isEmpty();
    }
}
