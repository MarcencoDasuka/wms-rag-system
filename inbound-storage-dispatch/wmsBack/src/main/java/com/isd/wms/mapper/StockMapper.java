package com.isd.wms.mapper;

import com.isd.wms.dto.inventory.StockResponse;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StockMapper {

    public StockResponse toResponse(Stock stock) {
        Optional<Product> product = stock.getProduct();
        Optional<Location> location = Optional.ofNullable(stock.getLocation());
        return new StockResponse(
                stock.getId(),
                product.map(Product::getBarcode).orElse(null),
                product.map(Product::getId).orElse(null),
                product.map(Product::getName).orElse(null),
                location.map(Location::getId).orElse(null),
                location.map(Location::getBarcode).orElse(null),
                stock.getQuantity(),
                stock.getReservedQuantity(),
                stock.getQuantity() - stock.getReservedQuantity(),
                stock.getManufactureDate(),
                stock.getExpirationDate()
        );
    }
}
