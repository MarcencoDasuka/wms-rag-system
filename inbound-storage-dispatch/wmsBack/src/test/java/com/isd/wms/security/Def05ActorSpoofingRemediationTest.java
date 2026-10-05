package com.isd.wms.security;

import com.isd.wms.dto.inventory.AddStockRequest;
import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.dto.inventory.RemoveStockRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.InventoryAdjustmentReason;
import com.isd.wms.enums.Role;
import com.isd.wms.mapper.InventoryHistoryMapper;
import com.isd.wms.mapper.StockMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.inventoryadjustment.InventoryAdjustmentContext;
import com.isd.wms.service.inventoryadjustment.InventoryAdjustmentValidator;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Def05ActorSpoofingRemediationTest {

    private StockRepository stockRepository;
    private InventoryHistoryRepository historyRepository;
    private ProductRepository productRepository;
    private LocationRepository locationRepository;
    private UserRepository userRepository;
    private SecurityFacade securityFacade;

    private InventoryService inventoryService;
    private InventoryAdjustmentValidator adjustmentValidator;

    private User authenticatedUser;
    private User victimUser;
    private Product product;
    private Location location;
    private Stock stock;

    private InventoryHistory savedHistory;

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        authenticatedUser = new User("real.supervisor", "real@isd.com", "pass", Role.ROLE_SUPERVISOR, true, null, null);
        ReflectionTestUtils.setField(authenticatedUser, "id", 10L);

        victimUser = new User("innocent.victim", "victim@isd.com", "pass", Role.ROLE_OPERATOR, true, null, null);
        ReflectionTestUtils.setField(victimUser, "id", 99L);

        product = new Product("Paper", "SKU-PAP", null, null);
        ReflectionTestUtils.setField(product, "id", 100L);

        location = new Location("Shelf", "LOC-01", null, null, true);
        ReflectionTestUtils.setField(location, "id", 200L);
        location.setAvailable(true);

        stock = new Stock(product, location);
        ReflectionTestUtils.setField(stock, "id", 300L);
        stock.setQuantity(50);
        stock.setReservedQuantity(0);

        stockRepository = createProxy(StockRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName()) || "findByIdWithLock".equals(method.getName())) {
                return Optional.of(stock);
            }
            if ("existsByLocationAndAvailableIsTrueAndProductIsNot".equals(method.getName())) {
                return false;
            }
            if ("findByProductIdAndLocationId".equals(method.getName())) {
                return Optional.of(stock);
            }
            if ("save".equals(method.getName())) {
                return args[0];
            }
            return null;
        });

        historyRepository = createProxy(InventoryHistoryRepository.class, (proxy, method, args) -> {
            if ("save".equals(method.getName())) {
                savedHistory = (InventoryHistory) args[0];
                return savedHistory;
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
            Long id = (Long) args[0];
            if (Long.valueOf(10L).equals(id)) return Optional.of(authenticatedUser);
            if (Long.valueOf(99L).equals(id)) return Optional.of(victimUser);
            return Optional.empty();
        });

        // SecurityFacade returning authenticated user
        securityFacade = new SecurityFacade(userRepository) {
            @Override
            public String getCurrentUsername() {
                return authenticatedUser.getUsername();
            }

            @Override
            public User getCurrentUser() {
                return authenticatedUser;
            }
        };

        inventoryService = new InventoryService(
                stockRepository, historyRepository, productRepository, locationRepository,
                userRepository, new StockMapper(), new InventoryHistoryMapper(),
                null, null, null, securityFacade
        );

        adjustmentValidator = new InventoryAdjustmentValidator(stockRepository, userRepository, securityFacade);
    }

    @Test
    @DisplayName("DEF-05: addStock must record authenticated principal in history, ignoring spoofed userId in request")
    void addStock_withSpoofedUserId_mustRecordAuthenticatedUserInHistory() {
        // Attacker sends victim's ID (99L) in body
        AddStockRequest requestWithSpoofedId = new AddStockRequest(
                100L, 200L, 10, 0,
                LocalDate.now(), LocalDate.now().plusMonths(6),
                99L // Spoofed victim ID
        );

        inventoryService.addStock(requestWithSpoofedId);

        assertThat(savedHistory).isNotNull();
        assertThat(savedHistory.getUser())
                .as("Audit history MUST record the authenticated user from SecurityContext, NOT the spoofed userId")
                .isEqualTo(authenticatedUser);
        assertThat(savedHistory.getUser().getId())
                .isEqualTo(10L)
                .isNotEqualTo(99L);
    }

    @Test
    @DisplayName("DEF-05: removeStock must record authenticated principal in history, ignoring spoofed userId in request")
    void removeStock_withSpoofedUserId_mustRecordAuthenticatedUserInHistory() {
        RemoveStockRequest requestWithSpoofedId = new RemoveStockRequest(300L, 5, 99L);

        inventoryService.removeStock(requestWithSpoofedId);

        assertThat(savedHistory).isNotNull();
        assertThat(savedHistory.getUser())
                .as("Audit history MUST record the authenticated user from SecurityContext, NOT the spoofed userId")
                .isEqualTo(authenticatedUser);
        assertThat(savedHistory.getUser().getId())
                .isEqualTo(10L)
                .isNotEqualTo(99L);
    }

    @Test
    @DisplayName("DEF-05: InventoryAdjustmentValidator must load authenticated user into context, ignoring request.userId")
    void validateAndLoad_withSpoofedUserId_mustLoadAuthenticatedUserIntoContext() {
        InventoryAdjustmentRequest requestWithSpoofedId = new InventoryAdjustmentRequest(
                40, 99L, InventoryAdjustmentReason.DAMAGED, "Audit check", null, null
        );

        InventoryAdjustmentContext context = adjustmentValidator.validateAndLoad(300L, requestWithSpoofedId);

        assertThat(context.user())
                .as("Adjustment context user MUST be authenticated principal, NOT the spoofed userId")
                .isEqualTo(authenticatedUser);
        assertThat(context.user().getId())
                .isEqualTo(10L)
                .isNotEqualTo(99L);
    }
}
