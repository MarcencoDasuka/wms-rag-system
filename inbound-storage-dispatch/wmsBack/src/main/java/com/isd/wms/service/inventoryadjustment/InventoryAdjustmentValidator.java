package com.isd.wms.service.inventoryadjustment;

import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.User;
import com.isd.wms.exception.StockNotFoundException;
import com.isd.wms.exception.UserNotFoundException;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryAdjustmentValidator {

    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    public InventoryAdjustmentContext validateAndLoad(Long stockId, InventoryAdjustmentRequest request) {
        Stock stock = stockRepository.findById(stockId)
            .orElseThrow(() -> new StockNotFoundException(stockId));
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new UserNotFoundException(request.userId()));
        Product product = stock.getProduct()
            .orElseThrow(() -> new IllegalStateException("Stock has no product"));

        return new InventoryAdjustmentContext(
            stockId,
            request,
            stock,
            user,
            product,
            InventoryAdjustmentSupport.nullSafeQuantity(stock.getQuantity())
        );
    }
}
