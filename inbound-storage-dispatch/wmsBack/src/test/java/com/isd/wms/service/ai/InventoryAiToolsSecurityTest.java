package com.isd.wms.service.ai;

import com.isd.wms.dto.inventory.AddStockRequest;
import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.User;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.InventoryAdjustmentService;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryAiToolsSecurityTest {

    @Mock private ProductRepository productRepository;
    @Mock private StockRepository stockRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserRepository userRepository;
    @Mock private InventoryService inventoryService;
    @Mock private InventoryAdjustmentService inventoryAdjustmentService;
    @Mock private SecurityFacade securityFacade;
    @Mock private VectorStore vectorStore;
    @Mock private AiToolSecurityBoundary securityBoundary;

    private InventoryMutatingAiTools inventoryMutatingAiTools;
    private InventoryAiTools inventoryAiTools;

    @BeforeEach
    void setUp() {
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
            vectorStore,
            inventoryMutatingAiTools
        );
    }

    @Test
    @DisplayName("checkStockByBarcode is read-only and requires neither mutation permission nor confirmation")
    void checkStockByBarcode_readOnly_succeedsWithoutMutationBoundary() {
        Product p = new Product();
        p.setId(10L);
        p.setName("Juice");
        p.setBarcode("JUC-01");
        when(productRepository.findByBarcode("JUC-01")).thenReturn(Optional.of(p));

        String result = inventoryAiTools.checkStockByBarcode("JUC-01");

        assertThat(result).contains("Juice");
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(inventoryAdjustmentService);
        verifyNoInteractions(securityBoundary);
    }

    @Test
    @DisplayName("receiveInboundStock enforces authorization and rejects unauthorized caller")
    void receiveInboundStock_whenUnauthorized_throwsAccessDeniedException() {
        doThrow(new AccessDeniedException("Access denied: User lacks required role"))
            .when(securityBoundary).enforceSupervisorOrDev("receiveInboundStock");

        assertThatThrownBy(() -> inventoryMutatingAiTools.receiveInboundStock("BAR-1", 10, "LOC-1"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Access denied");

        verify(inventoryService, never()).addStock(any());
    }

    @Test
    @DisplayName("adjustInventoryStock requires confirmation token when called without one")
    void adjustInventoryStock_whenAuthorizedWithoutToken_returnsConfirmationPrompt() {
        Product p = new Product();
        p.setId(1L);
        p.setName("Box");
        p.setBarcode("BOX-01");
        Location loc = new Location();
        loc.setId(2L);
        loc.setBarcode("LOC-01");

        Stock stock = new Stock();
        stock.setId(100L);
        stock.setProduct(p);
        stock.setLocation(loc);
        stock.setQuantity(20);

        when(productRepository.findByBarcode("BOX-01")).thenReturn(Optional.of(p));
        when(locationRepository.findAll()).thenReturn(List.of(loc));
        when(stockRepository.findByProductIdAndLocationIdAndAvailableIsTrue(1L, 2L)).thenReturn(Optional.of(stock));

        when(securityBoundary.initiatePendingOperation(eq("ADJUST_INVENTORY_STOCK"), any(), eq(100L), any(), eq(15), eq("DAMAGED")))
            .thenReturn("PENDING HUMAN CONFIRMATION: ADJUST_INVENTORY_STOCK on target 'BOX-01@LOC-01'. Operation ID: CONFIRM-INV-1");

        String response = inventoryMutatingAiTools.adjustInventoryStock(
            "BOX-01", "LOC-01", 15, "DAMAGED", "Water leak", null
        );

        assertThat(response).contains("PENDING HUMAN CONFIRMATION");
        assertThat(response).contains("CONFIRM-INV-1");
        verify(inventoryAdjustmentService, never()).adjustStock(any(), any());
    }

    @Test
    @DisplayName("adjustInventoryStock throws AccessDeniedException when confirmation token is passed directly to AI tool")
    void adjustInventoryStock_whenAuthorizedWithToken_executesAdjustment() {
        assertThatThrownBy(() -> inventoryMutatingAiTools.adjustInventoryStock(
            "BOX-01", "LOC-01", 15, "DAMAGED", "Water leak", "CONFIRM-VALID-TOKEN"
        ))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessageContaining("Autonomous AI tool confirmation is disabled");
    }
}
