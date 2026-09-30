package com.isd.wms.service;

import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Stock;
import com.isd.wms.enums.Status;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that determines the execution order of allocations during the
 * picking process.
 * <p>
 * Allocations are ordered by the location they are picked from (grouped by
 * location, then sorted by creation time). This helps optimise the picker's
 * route. The service also provides methods to find the next executable
 * allocation after a given one.
 * </p>
 *
 * @see Allocation
 * @see Stock
 */
@Service
public class PickingFlowService {

    /**
     * Orders a list of allocations by their source location, grouping all
     * allocations from the same location together.
     *
     * @param allocations the list to order
     * @return a new list sorted by location (and creation time within each location)
     */
    public List<Allocation> orderAllocationsBySourceLocation(List<Allocation> allocations) {
        List<Allocation> sortedAllocations = new ArrayList<>(allocations);
        sortedAllocations.sort(Comparator.comparing(Allocation::getCreatedAt).thenComparing(Allocation::getId));

        Map<Long, List<Allocation>> groupedByLocation = new LinkedHashMap<>();
        for (Allocation allocation : sortedAllocations) {
            Long locationId = allocation.getStock().getLocation().getId();
            groupedByLocation.computeIfAbsent(locationId, ignored -> new ArrayList<>()).add(allocation);
        }

        return groupedByLocation.values().stream()
            .flatMap(List::stream)
            .toList();
    }

    /**
     * Finds the first executable allocation in the list (status is CREATED, ASSIGNED, or IN_PROGRESS)
     * after applying location grouping.
     *
     * @param allocations the list of allocations
     * @return an Optional containing the next executable allocation, or empty if none
     */
    public Optional<Allocation> findCurrentExecutableAllocation(List<Allocation> allocations) {
        return orderAllocationsBySourceLocation(allocations).stream()
            .filter(allocation -> allocation.getStatus() == Status.CREATED
                || allocation.getStatus() == Status.ASSIGNED
                || allocation.getStatus() == Status.IN_PROGRESS)
            .findFirst();
    }

    /**
     * Finds the next executable allocation after a completed allocation in the ordered list.
     *
     * @param allocations the full list of allocations
     * @param completedAllocation the allocation that was just completed
     * @return an Optional containing the next executable allocation, or empty if none
     */
    public Optional<Allocation> findNextExecutableAllocationAfter(
        List<Allocation> allocations,
        Allocation completedAllocation) {
        List<Allocation> orderedAllocations = orderAllocationsBySourceLocation(allocations);
        int completedIndex = orderedAllocations.indexOf(completedAllocation);

        if (completedIndex < 0) {
            return findCurrentExecutableAllocation(allocations);
        }

        for (int index = completedIndex + 1; index < orderedAllocations.size(); index++) {
            Allocation allocation = orderedAllocations.get(index);
            if (allocation.getStatus() == Status.CREATED
                || allocation.getStatus() == Status.ASSIGNED
                || allocation.getStatus() == Status.IN_PROGRESS) {
                return Optional.of(allocation);
            }
        }

        return Optional.empty();
    }
}
