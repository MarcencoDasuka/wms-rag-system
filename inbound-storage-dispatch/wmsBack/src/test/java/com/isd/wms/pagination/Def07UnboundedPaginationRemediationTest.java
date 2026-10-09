package com.isd.wms.pagination;

import com.isd.wms.controller.InventoryController;
import com.isd.wms.controller.OrderController;
import com.isd.wms.controller.ProductController;
import com.isd.wms.controller.UserController;
import com.isd.wms.dto.order.OrderResponse;
import com.isd.wms.dto.product.ProductResponse;
import com.isd.wms.dto.user.UserResponse;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.ProductService;
import com.isd.wms.service.UserService;
import com.isd.wms.util.PaginationUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class Def07UnboundedPaginationRemediationTest {

    @Test
    @DisplayName("clampPageable: null or unpaged input falls back to default size and page 0")
    void clampPageable_nullOrUnpaged_usesDefaultSize() {
        Pageable nullResult = PaginationUtils.clampPageable(null, 50, 200);
        assertThat(nullResult.getPageNumber()).isZero();
        assertThat(nullResult.getPageSize()).isEqualTo(50);

        Pageable unpagedResult = PaginationUtils.clampPageable(Pageable.unpaged(), 100, 500);
        assertThat(unpagedResult.getPageNumber()).isZero();
        assertThat(unpagedResult.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("clampPageable: unpaged or non-positive requested size falls back to default size")
    void clampPageable_nonPositiveSize_usesDefaultSize() {
        Pageable unpaged = Pageable.unpaged();
        Pageable resultUnpaged = PaginationUtils.clampPageable(unpaged, 50, 200);
        assertThat(resultUnpaged.getPageSize()).isEqualTo(50);

        Pageable customNonPositive = new Pageable() {
            @Override public int getPageNumber() { return 2; }
            @Override public int getPageSize() { return 0; }
            @Override public long getOffset() { return 0; }
            @Override public Sort getSort() { return Sort.unsorted(); }
            @Override public Pageable next() { return this; }
            @Override public Pageable previousOrFirst() { return this; }
            @Override public Pageable first() { return this; }
            @Override public Pageable withPage(int pageNumber) { return this; }
            @Override public boolean hasPrevious() { return false; }
        };
        Pageable clampedZero = PaginationUtils.clampPageable(customNonPositive, 50, 200);
        assertThat(clampedZero.getPageSize()).isEqualTo(50);
        assertThat(clampedZero.getPageNumber()).isEqualTo(2);

        Pageable customNegative = new Pageable() {
            @Override public int getPageNumber() { return 1; }
            @Override public int getPageSize() { return -10; }
            @Override public long getOffset() { return 0; }
            @Override public Sort getSort() { return Sort.unsorted(); }
            @Override public Pageable next() { return this; }
            @Override public Pageable previousOrFirst() { return this; }
            @Override public Pageable first() { return this; }
            @Override public Pageable withPage(int pageNumber) { return this; }
            @Override public boolean hasPrevious() { return false; }
        };
        Pageable clampedNegative = PaginationUtils.clampPageable(customNegative, 100, 500);
        assertThat(clampedNegative.getPageSize()).isEqualTo(100);
        assertThat(clampedNegative.getPageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("clampPageable: requested size exceeding max is clamped to maxSize")
    void clampPageable_exceedingMaxSize_clampedToMax() {
        Pageable orderClamp = PaginationUtils.clampPageable(PageRequest.of(0, 10000), 50, 200);
        assertThat(orderClamp.getPageNumber()).isZero();
        assertThat(orderClamp.getPageSize()).isEqualTo(200);

        Pageable productClamp = PaginationUtils.clampPageable(PageRequest.of(3, 999999), 100, 500);
        assertThat(productClamp.getPageNumber()).isEqualTo(3);
        assertThat(productClamp.getPageSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("clampPageable: valid request within range preserves requested page, size, and sort")
    void clampPageable_validWithinRange_preserved() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable input = PageRequest.of(4, 75, sort);
        Pageable clamped = PaginationUtils.clampPageable(input, 50, 200);

        assertThat(clamped.getPageNumber()).isEqualTo(4);
        assertThat(clamped.getPageSize()).isEqualTo(75);
        assertThat(clamped.getSort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("toPagedResponse: returns flat List body while setting all standard pagination headers")
    void toPagedResponse_preservesFlatListBodyAndHeaders() {
        List<String> items = List.of("Item A", "Item B", "Item C");
        Page<String> page = new PageImpl<>(items, PageRequest.of(2, 25), 140);

        ResponseEntity<List<String>> response = PaginationUtils.toPagedResponse(page);

        assertThat(response.getBody()).isEqualTo(items);
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("140");
        assertThat(response.getHeaders().getFirst("X-Total-Pages")).isEqualTo("6");
        assertThat(response.getHeaders().getFirst("X-Current-Page")).isEqualTo("2");
        assertThat(response.getHeaders().getFirst("X-Page-Size")).isEqualTo("25");
    }

    @Test
    @DisplayName("OrderController: getOrders bounds unbounded request to ORDER_DEFAULT_SIZE (50) and returns flat list")
    void orderController_boundsUnboundedRequest() {
        AtomicReference<Pageable> capturedPageable = new AtomicReference<>();
        OrderResponse mockOrder = new OrderResponse(1L, "ORD-1", 10L, OrderStatus.CREATED, LocalDateTime.now(), LocalDateTime.now());

        OrderService mockService = new OrderService(null, null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public Page<OrderResponse> getAllOrders(Pageable p) {
                capturedPageable.set(p);
                return new PageImpl<>(List.of(mockOrder), p, 1L);
            }
        };

        OrderController controller = new OrderController(mockService);
        ResponseEntity<List<OrderResponse>> response = controller.getOrders(Pageable.unpaged());

        assertThat(capturedPageable.get()).isNotNull();
        assertThat(capturedPageable.get().getPageSize()).isEqualTo(PaginationUtils.ORDER_DEFAULT_SIZE);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
    }

    @Test
    @DisplayName("OrderController: getOrders clamps oversized request to ORDER_MAX_SIZE (200)")
    void orderController_clampsOversizedRequest() {
        AtomicReference<Pageable> capturedPageable = new AtomicReference<>();
        OrderService mockService = new OrderService(null, null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public Page<OrderResponse> getAllOrders(Pageable p) {
                capturedPageable.set(p);
                return new PageImpl<>(List.of(), p, 0L);
            }
        };

        OrderController controller = new OrderController(mockService);
        controller.getOrders(PageRequest.of(0, 5000));

        assertThat(capturedPageable.get()).isNotNull();
        assertThat(capturedPageable.get().getPageSize()).isEqualTo(PaginationUtils.ORDER_MAX_SIZE);
    }

    @Test
    @DisplayName("ProductController: getAllProducts bounds unpaged request to PRODUCT_DEFAULT_SIZE (100) and clamps to PRODUCT_MAX_SIZE (500)")
    void productController_boundsProductRequests() {
        AtomicReference<Pageable> capturedPageable = new AtomicReference<>();
        ProductService mockService = new ProductService(null, null, null, null, null) {
            @Override
            public Page<ProductResponse> getAllProducts(Pageable p) {
                capturedPageable.set(p);
                return new PageImpl<>(List.of(), p, 0L);
            }
        };

        ProductController controller = new ProductController(mockService);

        controller.getAllProducts(Pageable.unpaged());
        assertThat(capturedPageable.get().getPageSize()).isEqualTo(PaginationUtils.PRODUCT_DEFAULT_SIZE);

        controller.getAllProducts(PageRequest.of(0, 9999));
        assertThat(capturedPageable.get().getPageSize()).isEqualTo(PaginationUtils.PRODUCT_MAX_SIZE);
    }

    @Test
    @DisplayName("InventoryController: getAllStock bounds unpaged to INVENTORY_DEFAULT_SIZE (50) and clamps to INVENTORY_MAX_SIZE (200)")
    void inventoryController_boundsStockRequests() {
        AtomicReference<Pageable> capturedPageable = new AtomicReference<>();
        InventoryService mockService = new InventoryService(null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public Page<com.isd.wms.dto.inventory.StockResponse> getAllStock(Pageable p) {
                capturedPageable.set(p);
                return new PageImpl<>(List.of(), p, 0L);
            }
        };

        InventoryController controller = new InventoryController(mockService, null);

        controller.getAllStock(Pageable.unpaged());
        assertThat(capturedPageable.get().getPageSize()).isEqualTo(PaginationUtils.INVENTORY_DEFAULT_SIZE);

        controller.getAllStock(PageRequest.of(0, 5000));
        assertThat(capturedPageable.get().getPageSize()).isEqualTo(PaginationUtils.INVENTORY_MAX_SIZE);
    }

    @Test
    @DisplayName("UserController: getAllUsers bounds unpaged to USER_DEFAULT_SIZE (50) and clamps to USER_MAX_SIZE (200)")
    void userController_boundsUserRequests() {
        AtomicReference<Pageable> capturedPageable = new AtomicReference<>();
        UserService mockService = new UserService(null, null, null, null, null) {
            @Override
            public Page<UserResponse> getAllUsers(Pageable p) {
                capturedPageable.set(p);
                return new PageImpl<>(List.of(), p, 0L);
            }
        };

        UserController controller = new UserController(mockService);

        controller.getAllUsers(Pageable.unpaged());
        assertThat(capturedPageable.get().getPageSize()).isEqualTo(PaginationUtils.USER_DEFAULT_SIZE);

        controller.getAllUsers(PageRequest.of(0, 8000));
        assertThat(capturedPageable.get().getPageSize()).isEqualTo(PaginationUtils.USER_MAX_SIZE);
    }
}
