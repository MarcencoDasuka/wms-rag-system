package com.isd.wms.service.allocation;

import com.isd.wms.entity.Stock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PickingAllocationStrategyTest {

    private final PickingAllocationStrategy strategy = new PickingAllocationStrategy();

    @Test
    void sortStocks_ordersByAvailableQuantityDescending() {
        Stock smallerStock = new Stock();
        smallerStock.setId(2L);
        smallerStock.setQuantity(40);
        smallerStock.setReservedQuantity(0);

        Stock largerStock = new Stock();
        largerStock.setId(1L);
        largerStock.setQuantity(60);
        largerStock.setReservedQuantity(0);

        List<Stock> stocks = new ArrayList<>(List.of(smallerStock, largerStock));

        strategy.sortStocks(stocks);

        assertThat(stocks).containsExactly(largerStock, smallerStock);
    }
}
