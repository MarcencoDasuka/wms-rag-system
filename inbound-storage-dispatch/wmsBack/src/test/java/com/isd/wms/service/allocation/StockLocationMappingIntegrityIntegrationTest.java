package com.isd.wms.service.allocation;

import com.isd.wms.dto.inventory.AddStockRequest;
import com.isd.wms.dto.order.ExtendedOrderCreateRequest;
import com.isd.wms.dto.order.OrderCreateRequest;
import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.enums.Zone;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.*;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.LocationService;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.ReplenishmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StockLocationMappingIntegrityIntegrationTest {

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReplenishmentRepository replenishmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private LocationService locationService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ReplenishmentService replenishmentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            testUser = userRepository.findByUsername("supervisor")
                .orElseGet(() -> userRepository.save(new User("supervisor", "sup@test.com", "pass", Role.ROLE_SUPERVISOR, true, null, null)));
            testCategory = categoryRepository.save(new Category("Cat_D2_" + UUID.randomUUID()));
            return null;
        });
    }

    @Test
    @DisplayName("D-2: Multiple stocks at same location (historical depleted and active) map correctly via @ManyToOne without NonUniqueResultException")
    void multipleStocksAtSameLocation_mappedWithManyToOne_loadsSuccessfullyWithoutNonUniqueResultException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long[] fixtureIds = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Product prodA = productRepository.save(new Product("ProdA_" + suffix, "BARA_" + suffix, "Desc A", testCategory));
            Product prodB = productRepository.save(new Product("ProdB_" + suffix, "BARB_" + suffix, "Desc B", testCategory));

            Location loc = locationRepository.save(new Location("LOC_D2_" + suffix, "BC_D2_" + suffix, Zone.PICKING, "Test Loc"));

            // Stock 1 for Product A: depleted (available = false, quantity = 0)
            Stock stockA = new Stock(prodA, loc, 0, 0, LocalDate.now(), LocalDate.now().plusMonths(6));
            stockA.setAvailable(false);
            stockA = stockRepository.save(stockA);

            // Stock 2 for Product B: active (available = true, quantity = 15)
            Stock stockB = new Stock(prodB, loc, 15, 0, LocalDate.now(), LocalDate.now().plusMonths(6));
            stockB.setAvailable(true);
            stockB = stockRepository.save(stockB);

            return new Long[]{loc.getId(), stockA.getId(), stockB.getId(), prodA.getId(), prodB.getId()};
        });

        Long locId = fixtureIds[0];
        Long stockAId = fixtureIds[1];
        Long stockBId = fixtureIds[2];
        Long prodBId = fixtureIds[4];

        tx.execute(status -> {
            // 1. Verify findByLocationId and findAllByLocationId return all stocks without NonUniqueResultException
            List<Stock> allStocks = stockRepository.findAllByLocationId(locId);
            assertThat(allStocks).hasSize(2);
            assertThat(allStocks).extracting(Stock::getId).containsExactlyInAnyOrder(stockAId, stockBId);

            List<Stock> queryStocks = stockRepository.findByLocationId(locId);
            assertThat(queryStocks).hasSize(2);

            // 2. Verify findByLocationIdAndAvailableIsTrue returns only the active stock
            Stock activeStock = stockRepository.findByLocationIdAndAvailableIsTrue(locId).orElseThrow();
            assertThat(activeStock.getId()).isEqualTo(stockBId);
            assertThat(activeStock.getQuantity()).isEqualTo(15);
            assertThat(activeStock.getProduct().orElseThrow().getId()).isEqualTo(prodBId);

            // 3. Verify @ManyToOne mapping loads the location navigation properly
            assertThat(activeStock.getLocation().getId()).isEqualTo(locId);
            return null;
        });
    }

    @Test
    @DisplayName("D-2: Deleting a location is rejected if it has active stock, replenishments, or orders; empty location sets isActive=false and available=false")
    void deleteLocation_lifecycleIntegrityChecks() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Location locWithStock = locationRepository.save(new Location("LOC_STK_" + suffix, "BC_STK_" + suffix, Zone.PICKING, "Loc with stock"));
        Product prod = productRepository.save(new Product("Prod_" + suffix, "BAR_" + suffix, "Desc", testCategory));
        stockRepository.save(new Stock(prod, locWithStock, 10, 0, LocalDate.now(), LocalDate.now().plusMonths(6)));

        // 1. Cannot delete location with active stock
        assertThatThrownBy(() -> locationService.deleteLocation(locWithStock.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("You cannot delete a location while there is a product in it");

        // 2. Cannot delete location with active replenishment
        Location locWithRepl = locationRepository.save(new Location("LOC_RPL_" + suffix, "BC_RPL_" + suffix, Zone.PICKING, "Loc with repl"));
        Replenishment repl = new Replenishment(prod, 10, locWithRepl);
        repl.setStatus(Status.IN_PROGRESS);
        repl.setLogicId("RPL-" + suffix.toUpperCase());
        replenishmentRepository.save(repl);

        assertThatThrownBy(() -> locationService.deleteLocation(locWithRepl.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active replenishment");

        // 3. Cannot delete location with active order
        Location locWithOrder = locationRepository.save(new Location("LOC_ORD_" + suffix, "BC_ORD_" + suffix, Zone.DISPATCH, "Loc with order"));
        Order order = new Order("ORD-" + suffix.toUpperCase(), locWithOrder);
        order.setStatus(OrderStatus.CREATED);
        orderRepository.save(order);

        assertThatThrownBy(() -> locationService.deleteLocation(locWithOrder.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active orders");

        // 4. Deleting an empty unused location synchronizes isActive=false and available=false
        Location cleanLoc = locationRepository.save(new Location("LOC_CLN_" + suffix, "BC_CLN_" + suffix, Zone.PICKING, "Clean Loc"));
        locationService.deleteLocation(cleanLoc.getId());

        Location deletedLoc = locationRepository.findById(cleanLoc.getId()).orElseThrow();
        assertThat(deletedLoc.getIsActive()).isFalse();
        assertThat(deletedLoc.getAvailable()).isFalse();
    }

    @Test
    @DisplayName("D-2: Inactive or unavailable locations reject stock additions, replenishments, and orders")
    void inactiveOrUnavailableLocation_rejectsMutations() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Location inactiveLoc = locationRepository.save(new Location("LOC_INACT_" + suffix, "BC_INACT_" + suffix, Zone.PICKING, "Inactive Loc"));
        locationService.deleteLocation(inactiveLoc.getId());

        Location unavailableLoc = locationRepository.save(new Location("LOC_UNAV_" + suffix, "BC_UNAV_" + suffix, Zone.PICKING, "Unavail Loc", false));

        Product prod = productRepository.save(new Product("Prod_MUT_" + suffix, "BAR_MUT_" + suffix, "Desc", testCategory));

        // 1. Cannot add stock to inactive location
        assertThatThrownBy(() -> inventoryService.addStock(new AddStockRequest(
            prod.getId(), inactiveLoc.getId(), 10, 0, LocalDate.now(), LocalDate.now().plusMonths(6), testUser.getId()
        )))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Cannot add stock to an inactive or unavailable location");

        // 2. Cannot add stock to unavailable location
        assertThatThrownBy(() -> inventoryService.addStock(new AddStockRequest(
            prod.getId(), unavailableLoc.getId(), 10, 0, LocalDate.now(), LocalDate.now().plusMonths(6), testUser.getId()
        )))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Cannot add stock to an inactive or unavailable location");

        // 3. Cannot route replenishment to inactive location
        assertThatThrownBy(() -> replenishmentService.createReplenishment(new ReplenishmentCreateRequest(
            prod.getId(), 5, inactiveLoc.getId()
        )))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Cannot route replenishment to inactive or unavailable location");

        // 4. Cannot route order to inactive destination location
        ExtendedOrderCreateRequest orderReq = new ExtendedOrderCreateRequest(
            new OrderCreateRequest("ORD-INACT-" + suffix, inactiveLoc.getId()),
            List.of(new OrderLineCreateRequest(null, prod.getId(), 5))
        );
        assertThatThrownBy(() -> orderService.addExtendedOrder(orderReq))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Cannot route order to inactive or unavailable destination location");
    }
}
