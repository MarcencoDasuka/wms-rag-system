package com.isd.wms.mapper;

import com.isd.wms.dto.inventory.InventoryHistoryResponse;
import com.isd.wms.entity.InventoryHistory;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.User;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InventoryHistoryMapper {

    public InventoryHistoryResponse toResponse(InventoryHistory history) {
        Optional<Product> product = Optional.ofNullable(history.getProduct());
        Optional<Location> sourceLocation = Optional.ofNullable(history.getSourceLocation());
        Optional<Location> destinationLocation = Optional.ofNullable(history.getDestinationLocation());
        Optional<User> user = Optional.ofNullable(history.getUser());
        return new InventoryHistoryResponse(
                history.getId(),
                product.map(Product::getId).orElse(null),
                product.map(Product::getName).orElse(null),
                product.map(Product::getBarcode).orElse(history.getBarcode()),
                history.getAlteredQuantity(),
                history.getQuantityAfterChange(),
                sourceLocation.map(Location::getId).orElse(null),
                sourceLocation.map(Location::getBarcode).orElse(null),
                destinationLocation.map(Location::getId).orElse(null),
                destinationLocation.map(Location::getBarcode).orElse(null),
                history.getOperationType().name(),
                history.getTimestamp(),
                user.map(User::getId).orElse(null),
                user.map(User::getUsername).orElse(null)
        );
    }
}
