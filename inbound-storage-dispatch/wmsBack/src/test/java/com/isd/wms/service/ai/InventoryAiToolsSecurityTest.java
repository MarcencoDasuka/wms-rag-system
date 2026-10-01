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
        when(stockRepository.findAllByAvailableIsTrue()).thenReturn(List.of());

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
        when(stockRepository.findAllByAvailableIsTrue()).thenReturn(List.of(stock));

        String targetId = "BOX-01@LOC-01";
        when(securityBoundary.requireConfirmation(eq("ADJUST_INVENTORY_STOCK"), eq(targetId), any(), isNull()))
            .thenReturn("CONFIRMATION REQUIRED: ADJUST_INVENTORY_STOCK on target 'BOX-01@LOC-01'. To confirm, call with confirmationToken='CONFIRM-INV-1'.");

        String response = inventoryMutatingAiTools.adjustInventoryStock(
            "BOX-01", "LOC-01", 15, "DAMAGED", "Water leak", null
        );

        assertThat(response).contains("CONFIRMATION REQUIRED");
        assertThat(response).contains("CONFIRM-INV-1");
        verify(inventoryAdjustmentService, never()).adjustStock(any(), any());
    }

    @Test
    @DisplayName("adjustInventoryStock executes adjustment and audits when valid confirmation token is provided")
    void adjustInventoryStock_whenAuthorizedWithToken_executesAdjustment() {
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

        User user = new User();
        user.setId(5L);

        when(productRepository.findByBarcode("BOX-01")).thenReturn(Optional.of(p));
        when(locationRepository.findAll()).thenReturn(List.of(loc));
        when(stockRepository.findAllByAvailableIsTrue()).thenReturn(List.of(stock));
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");
        when(userRepository.findByUsername("supervisor_dan")).thenReturn(Optional.of(user));

        String targetId = "BOX-01@LOC-01";
        // Valid token returns null from requireConfirmation
        when(securityBoundary.requireConfirmation(eq("ADJUST_INVENTORY_STOCK"), eq(targetId), any(), eq("CONFIRM-VALID-TOKEN")))
            .thenReturn(null);

        String response = inventoryMutatingAiTools.adjustInventoryStock(
            "BOX-01", "LOC-01", 15, "DAMAGED", "Water leak", "CONFIRM-VALID-TOKEN"
        );

        assertThat(response).contains("Success! Stock adjusted to 15. Reason: DAMAGED.");
        verify(inventoryAdjustmentService).adjustStock(eq(100L), any(InventoryAdjustmentRequest.class));
        verify(securityBoundary).auditMutation(eq("adjustInventoryStock"), eq(targetId), any());
    }
}
