package com.isd.wms.service;

import com.isd.wms.dto.inventory.AddStockRequest;
import com.isd.wms.dto.inventory.RemoveStockRequest;
import com.isd.wms.dto.inventory.StockResponse;
import com.isd.wms.entity.InventoryHistory;
import com.isd.wms.enums.InventoryOperationType;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.exception.InsufficientStockException;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.InventoryHistoryMapper;
import com.isd.wms.mapper.StockMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest {

    private StockRepository stockRepository;
    private InventoryHistoryRepository inventoryHistoryRepository;
    private ProductRepository productRepository;
    private LocationRepository locationRepository;
    private UserRepository userRepository;
    private SecurityFacade securityFacade;

    private StockMapper stockMapper = new StockMapper();
    private InventoryHistoryMapper historyMapper = new InventoryHistoryMapper();

    private InventoryService inventoryService;

    private Product product;
    private Location location;
    private User user;

    private final AtomicReference<InventoryHistory> savedHistoryRef = new AtomicReference<>();
    private final AtomicReference<Boolean> existsDifferentProduct = new AtomicReference<>(false);
    private final AtomicReference<Stock> stockFindResult = new AtomicReference<>(null);

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        product = new Product("Milk", "SKU-1", null, null);
        ReflectionTestUtils.setField(product, "id", 1L);

        location = new Location("Loc", "A-01", null, null, true);
        ReflectionTestUtils.setField(location, "id", 2L);

        user = new User("supervisor", "s@test.com", "pass", Role.ROLE_SUPERVISOR, true, null, null);
        ReflectionTestUtils.setField(user, "id", 3L);

        savedHistoryRef.set(null);
        existsDifferentProduct.set(false);
        stockFindResult.set(null);

        stockRepository = createProxy(StockRepository.class, (proxy, method, args) -> {
            if ("existsByLocationAndAvailableIsTrueAndProductIsNot".equals(method.getName())) {
                return existsDifferentProduct.get();
            }
            if ("findByProductIdAndLocationId".equals(method.getName())) {
                return Optional.ofNullable(stockFindResult.get());
            }
            if ("findById".equals(method.getName()) || "findByIdWithLock".equals(method.getName())) {
                return Optional.ofNullable(stockFindResult.get());
            }
            if ("save".equals(method.getName())) {
                Stock s = (Stock) args[0];
                if (s.getId() == null) {
                    ReflectionTestUtils.setField(s, "id", 10L);
                }
                return s;
            }
            return null;
        });

        inventoryHistoryRepository = createProxy(InventoryHistoryRepository.class, (proxy, method, args) -> {
            if ("save".equals(method.getName())) {
                InventoryHistory h = (InventoryHistory) args[0];
                savedHistoryRef.set(h);
                return h;
            }
            return null;
        });

        productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.of(product);
            }
            return null;
        });

        locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName()) || "findByIdWithLock".equals(method.getName())) {
                return Optional.of(location);
            }
            return null;
        });

        userRepository = createProxy(UserRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.of(user);
            }
            return null;
        });

        securityFacade = new SecurityFacade(userRepository) {
            @Override
            public User getCurrentUser() {
                return user;
            }

            @Override
            public String getCurrentUsername() {
                return user.getUsername();
            }
        };

        inventoryService = new InventoryService(
                stockRepository, inventoryHistoryRepository, productRepository, locationRepository,
                userRepository, stockMapper, historyMapper,
                null, null, null, securityFacade
        );
    }

    @Test
    void addsNewStockAndCreatesHistory_emptyLocation() {
        StockResponse response = inventoryService.addStock(new AddStockRequest(
                1L, 2L, 5, 0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 3L
        ));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getQuantity()).isEqualTo(5);

        InventoryHistory history = savedHistoryRef.get();
        assertThat(history).isNotNull();
        assertThat(history.getOperationType()).isEqualTo(InventoryOperationType.ADD_STOCK);
        assertThat(history.getAlteredQuantity()).isEqualTo(5);
    }

    @Test
    void rejectsAddStock_differentProductOnLocation() {
        existsDifferentProduct.set(true);

        assertThatThrownBy(() -> inventoryService.addStock(new AddStockRequest(1L, 2L, 5, 0, null, null, 3L)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("occupied by a different product");
    }

    @Test
    void removesStockAndCreatesHistory() {
        Stock stock = new Stock(product, location);
        stock.setQuantity(8);
        ReflectionTestUtils.setField(stock, "id", 10L);
        stockFindResult.set(stock);

        StockResponse response = inventoryService.removeStock(new RemoveStockRequest(10L, 3, 3L));

        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(savedHistoryRef.get()).isNotNull();
    }

    @Test
    void rejectsRemovingMoreThanAvailableStock() {
        Stock stock = new Stock(product, location);
        stock.setQuantity(2);
        stock.setReservedQuantity(0);
        ReflectionTestUtils.setField(stock, "id", 10L);
        stockFindResult.set(stock);

        assertThatThrownBy(() -> inventoryService.removeStock(new RemoveStockRequest(10L, 3, 3L)))
                .isInstanceOf(InsufficientStockException.class);
    }
}
