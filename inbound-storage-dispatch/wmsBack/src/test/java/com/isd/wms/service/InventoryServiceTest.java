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
import com.isd.wms.service.imports.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private InventoryHistoryRepository inventoryHistoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private InventoryAdjustmentService inventoryAdjustmentService;
    @Mock private ImportService importService;

    @Spy private StockMapper stockMapper = new StockMapper();
    @Spy private InventoryHistoryMapper historyMapper = new InventoryHistoryMapper();

    @InjectMocks
    private InventoryService inventoryService;

    private Product product;
    private Location location;
    private User user;

    @BeforeEach
    void setUp() {
        product = new Product("Milk", "SKU-1", null, null);
        ReflectionTestUtils.setField(product, "id", 1L);
        location = new Location("Loc", "A-01", null, null, true);
        ReflectionTestUtils.setField(location, "id", 2L);
        user = new User("supervisor", "s@test.com", "pass", Role.ROLE_SUPERVISOR, true, null, null);
        ReflectionTestUtils.setField(user, "id", 3L);
    }

    @Test
    void addsNewStockAndCreatesHistory_emptyLocation() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(location));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        when(stockRepository.findByLocationId(2L)).thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> {
            Stock savedStock = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedStock, "id", 10L);
            return savedStock;
        });

        StockResponse response = inventoryService.addStock(new AddStockRequest(
            1L, 2L, 5, 0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 3L
        ));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getQuantity()).isEqualTo(5);

        ArgumentCaptor<InventoryHistory> historyCaptor = ArgumentCaptor.forClass(InventoryHistory.class);
        verify(inventoryHistoryRepository).save(historyCaptor.capture());
        InventoryHistory history = historyCaptor.getValue();
        assertThat(history.getOperationType()).isEqualTo(InventoryOperationType.ADD_STOCK);
        assertThat(history.getAlteredQuantity()).isEqualTo(5);
    }

    @Test
    void rejectsAddStock_differentProductOnLocation() {
        Product differentProduct = new Product("Juice", "SKU-2", null, null);
        ReflectionTestUtils.setField(differentProduct, "id", 99L);

        Stock existingStock = new Stock(differentProduct, location);
        existingStock.setQuantity(10);
        ReflectionTestUtils.setField(existingStock, "id", 10L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(location));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        when(stockRepository.findByLocationId(2L)).thenReturn(Optional.of(existingStock));

        assertThatThrownBy(() -> inventoryService.addStock(new AddStockRequest(1L, 2L, 5, 0, null, null, 3L)))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("occupied by a different product");
    }

    @Test
    void removesStockAndCreatesHistory() {
        Stock stock = new Stock(product, location);
        stock.setQuantity(8);
        ReflectionTestUtils.setField(stock, "id", 10L);

        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(stockRepository.save(stock)).thenReturn(stock);

        StockResponse response = inventoryService.removeStock(new RemoveStockRequest(10L, 3, 3L));

        assertThat(response.getQuantity()).isEqualTo(5);
        verify(inventoryHistoryRepository).save(any(InventoryHistory.class));
    }

    @Test
    void rejectsRemovingMoreThanAvailableStock() {
        Stock stock = new Stock(product, location);
        stock.setQuantity(2);
        stock.setReservedQuantity(0);
        ReflectionTestUtils.setField(stock, "id", 10L);

        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> inventoryService.removeStock(new RemoveStockRequest(10L, 3, 3L)))
            .isInstanceOf(InsufficientStockException.class);
    }
}
