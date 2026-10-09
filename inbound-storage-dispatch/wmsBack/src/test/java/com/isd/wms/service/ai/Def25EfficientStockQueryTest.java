package com.isd.wms.service.ai;

import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.enums.Zone;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.InventoryAdjustmentService;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class Def25EfficientStockQueryTest {

    private InventoryAiTools inventoryAiTools;
    private InventoryMutatingAiTools inventoryMutatingAiTools;

    private StockRepository stockRepository;
    private ProductRepository productRepository;
    private LocationRepository locationRepository;

    private Product testProduct;
    private Location testLocation;
    private Stock testStock;

    private final AtomicBoolean invokedFindAllByAvailableIsTrue = new AtomicBoolean(false);
    private final AtomicBoolean invokedFindAllByProductId = new AtomicBoolean(false);
    private final AtomicBoolean invokedFindAllByLocationId = new AtomicBoolean(false);
    private final AtomicBoolean invokedFindByProductAndLocation = new AtomicBoolean(false);

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "supervisor_alice",
            "pass",
            List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        invokedFindAllByAvailableIsTrue.set(false);
        invokedFindAllByProductId.set(false);
        invokedFindAllByLocationId.set(false);
        invokedFindByProductAndLocation.set(false);

        testProduct = new Product();
        ReflectionTestUtils.setField(testProduct, "id", 100L);
        testProduct.setName("Widget Pro");
        testProduct.setBarcode("BAR-PROD-100");
        testProduct.setDescription("Description");

        testLocation = new Location();
        ReflectionTestUtils.setField(testLocation, "id", 200L);
        testLocation.setBarcode("LOC-A-01");
        testLocation.setZone(Zone.PICKING);

        testStock = new Stock();
        ReflectionTestUtils.setField(testStock, "id", 300L);
        testStock.setProduct(testProduct);
        testStock.setLocation(testLocation);
        testStock.setQuantity(50);
        testStock.setReservedQuantity(5);
        testStock.setAvailable(true);

        productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> {
            if ("findByBarcode".equals(method.getName())) {
                String barcode = (String) args[0];
                if ("BAR-PROD-100".equals(barcode)) {
                    return Optional.of(testProduct);
                }
                return Optional.empty();
            }
            return null;
        });

        locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> {
            if ("findByBarcodeIgnoreCase".equals(method.getName())) {
                String barcode = (String) args[0];
                if ("LOC-A-01".equalsIgnoreCase(barcode)) {
                    return Optional.of(testLocation);
                }
                return Optional.empty();
            }
            if ("findAll".equals(method.getName())) {
                return List.of(testLocation);
            }
            return null;
        });

        stockRepository = createProxy(StockRepository.class, (proxy, method, args) -> {
            if ("findAllByAvailableIsTrue".equals(method.getName())) {
                invokedFindAllByAvailableIsTrue.set(true);
                return List.of(testStock);
            }
            if ("findAllByProductIdAndAvailableIsTrue".equals(method.getName())) {
                invokedFindAllByProductId.set(true);
                Long prodId = (Long) args[0];
                if (Long.valueOf(100L).equals(prodId)) {
                    return List.of(testStock);
                }
                return Collections.emptyList();
            }
            if ("findAllByLocationIdAndAvailableIsTrue".equals(method.getName())) {
                invokedFindAllByLocationId.set(true);
                Long locId = (Long) args[0];
                if (Long.valueOf(200L).equals(locId)) {
                    return List.of(testStock);
                }
                return Collections.emptyList();
            }
            if ("findByProductIdAndLocationIdAndAvailableIsTrue".equals(method.getName())) {
                invokedFindByProductAndLocation.set(true);
                Long prodId = (Long) args[0];
                Long locId = (Long) args[1];
                if (Long.valueOf(100L).equals(prodId) && Long.valueOf(200L).equals(locId)) {
                    return Optional.of(testStock);
                }
                return Optional.empty();
            }
            return null;
        });

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public String getCurrentUsername() {
                return "supervisor_alice";
            }
        };

        AiToolSecurityBoundary securityBoundary = new AiToolSecurityBoundary(
            securityFacade,
            createProxy(com.isd.wms.repository.OrderRepository.class, (p, m, a) -> null)
        );

        UserRepository userRepository = createProxy(UserRepository.class, (p, m, a) -> null);
        InventoryService inventoryService = new InventoryService(null, null, null, null, null, null, null, null, null, null, null);
        InventoryAdjustmentService inventoryAdjustmentService = new InventoryAdjustmentService(null, null, null, null, null);

        inventoryMutatingAiTools = new InventoryMutatingAiTools(
            productRepository,
            stockRepository,
            locationRepository,
            userRepository,
            inventoryService,
            inventoryAdjustmentService,
            securityFacade,
            securityBoundary
        );

        inventoryAiTools = new InventoryAiTools(
            productRepository,
            stockRepository,
            locationRepository,
            createProxy(VectorStore.class, (p, m, a) -> null),
            inventoryMutatingAiTools
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("DEF-25: checkStockByBarcode queries stock by productId in DB, never scanning entire table")
    void checkStockByBarcode_QueriesByProductId_NeverFullTable() {
        String result = inventoryAiTools.checkStockByBarcode("BAR-PROD-100");

        assertThat(invokedFindAllByProductId.get()).isTrue();
        assertThat(invokedFindAllByAvailableIsTrue.get()).isFalse();
        assertThat(result).contains("Widget Pro");
        assertThat(result).contains("LOC-A-01");
        assertThat(result).contains("50");
    }

    @Test
    @DisplayName("DEF-25: checkLocationItems queries stock by locationId in DB, never scanning entire table")
    void checkLocationItems_QueriesByLocationId_NeverFullTable() {
        String result = inventoryAiTools.checkLocationItems("LOC-A-01");

        assertThat(invokedFindAllByLocationId.get()).isTrue();
        assertThat(invokedFindAllByAvailableIsTrue.get()).isFalse();
        assertThat(result).contains("Widget Pro");
        assertThat(result).contains("Total Physical: 50");
    }

    @Test
    @DisplayName("DEF-25: adjustInventoryStock queries stock by productId and locationId in DB, never scanning entire table")
    void adjustInventoryStock_QueriesByProductAndLocation_NeverFullTable() {
        String result = inventoryMutatingAiTools.adjustInventoryStock(
            "BAR-PROD-100",
            "LOC-A-01",
            40,
            "INVENTORY_MISMATCH",
            "Periodic cycle count"
        );

        assertThat(invokedFindByProductAndLocation.get()).isTrue();
        assertThat(invokedFindAllByAvailableIsTrue.get()).isFalse();
        assertThat(result).contains("PENDING HUMAN CONFIRMATION");
    }
}
