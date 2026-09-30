package com.isd.wms.service.allocation;

import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShortageResolver {

    private final StockRepository stockRepository;
    private final AllocationRepository allocationRepository;

    @Transactional
    public List<Allocation> resolveShortage(Allocation sourceAllocation, int shortageQuantity, String taskContext) {
        log.info("{} shortage detected for allocation {} shortageQuantity={}", taskContext, sourceAllocation.getId(), shortageQuantity);

        Product product = sourceAllocation.getStock().getProduct()
            .orElseThrow(() -> new InvalidRequestException("Stock has no product"));

        List<Stock> alternativeStocks = stockRepository.findAvailableStocksByProductIdAndZone(
                product.getId(),
                sourceAllocation.getStock().getLocation().getZone()
            ).stream()
            .filter(stock -> !stock.getId().equals(sourceAllocation.getStock().getId()))
            .sorted(Comparator.comparing(this::availableQuantity).reversed().thenComparing(Stock::getId))
            .toList();

        List<Allocation> shortageAllocations = new ArrayList<>();
        int remaining = shortageQuantity;

        for (Stock stock : alternativeStocks) {
            if (remaining <= 0) break;

            int available = availableQuantity(stock);
            if (available <= 0) continue;

            int quantityToAllocate = Math.min(available, remaining);
            stock.setReservedQuantity(stock.getReservedQuantity() + quantityToAllocate);
            shortageAllocations.add(new Allocation(sourceAllocation.getTask(), stock, quantityToAllocate, Status.IN_PROGRESS));
            remaining -= quantityToAllocate;

            log.info("Alternative stock selected for {} shortage: allocationId={}, stockId={}, quantity={}",
                taskContext, sourceAllocation.getId(), stock.getId(), quantityToAllocate);
        }

        if (shortageAllocations.isEmpty()) {
            log.info("No alternative stock found for {} allocation {}", taskContext, sourceAllocation.getId());
        } else {
            allocationRepository.saveAll(shortageAllocations);
            stockRepository.saveAll(alternativeStocks);
        }

        return shortageAllocations;
    }

    private int availableQuantity(Stock stock) {
        return stock.getQuantity() - stock.getReservedQuantity();
    }
}
