package com.logistics.order.service.impl;

import com.logistics.order.allocation.AllocationStrategyResolver;
import com.logistics.order.allocation.WarehouseAllocationStrategy;
import com.logistics.order.allocation.model.AllocationPlan;
import com.logistics.order.allocation.model.AllocationRequest;
import com.logistics.order.allocation.model.RequestedLine;
import com.logistics.order.allocation.model.WarehouseCandidate;
import com.logistics.order.client.InventoryClient;
import com.logistics.order.domain.vo.GeoPoint;
import com.logistics.order.dto.request.AllocationPreviewRequest;
import com.logistics.order.dto.response.AllocationPreviewResponse;
import com.logistics.order.dto.response.AllocationStrategyResponse;
import com.logistics.order.exception.AllocationFailedException;
import com.logistics.order.service.AllocationPreviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simulation: what would happen, without anything happening.
 *
 * <p>Nothing here writes. No order is created, no stock is held, and the availability snapshot is
 * read once and reused for every strategy - which is what makes the comparison fair, since any
 * difference between the plans then comes from the rules rather than from stock having moved
 * between two reads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationPreviewServiceImpl implements AllocationPreviewService {

    private final InventoryClient inventoryClient;
    private final AllocationStrategyResolver strategyResolver;

    @Override
    public AllocationPreviewResponse preview(AllocationPreviewRequest request) {
        List<RequestedLine> lines = request.lines().stream()
                .map(line -> RequestedLine.of(line.productId(), line.quantity()))
                .toList();

        Set<String> productIds = lines.stream()
                .map(RequestedLine::productId)
                .collect(Collectors.toSet());

        // Read once, replay for every strategy.
        List<WarehouseCandidate> candidates = inventoryClient.fetchAvailability(productIds);

        GeoPoint destination = new GeoPoint(
                request.destination().latitude(), request.destination().longitude());
        AllocationRequest allocationRequest = new AllocationRequest(lines, destination, candidates);

        List<WarehouseAllocationStrategy> strategies = resolveStrategies(request.strategies());

        List<AllocationPreviewResponse.StrategyResult> results = new ArrayList<>();
        for (WarehouseAllocationStrategy strategy : strategies) {
            results.add(runOne(strategy, allocationRequest));
        }
        return new AllocationPreviewResponse(results);
    }

    @Override
    public List<AllocationStrategyResponse> availableStrategies() {
        return strategyResolver.all().stream()
                .map(strategy -> new AllocationStrategyResponse(
                        strategy.name(),
                        strategy.description(),
                        strategy.name().equals(strategyResolver.defaultStrategyName())))
                .toList();
    }

    private List<WarehouseAllocationStrategy> resolveStrategies(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            // Comparing all of them is the useful default for a simulation.
            return strategyResolver.all();
        }
        return requested.stream().map(strategyResolver::resolve).toList();
    }

    /**
     * An infeasible strategy is reported, not thrown.
     *
     * <p>The whole value of the endpoint is the side-by-side comparison, and "this rule cannot
     * serve the order" belongs in the table next to the ones that can.
     */
    private AllocationPreviewResponse.StrategyResult runOne(WarehouseAllocationStrategy strategy,
                                                            AllocationRequest request) {
        try {
            AllocationPlan plan = strategy.allocate(request);

            List<AllocationPreviewResponse.Segment> segments = plan.segments().stream()
                    .map(segment -> new AllocationPreviewResponse.Segment(
                            segment.warehouseId(),
                            segment.warehouseCode(),
                            segment.distanceKm(),
                            segment.shipmentSequence(),
                            segment.lines().stream()
                                    .map(line -> new AllocationPreviewResponse.Line(
                                            line.productId(), line.quantity()))
                                    .toList()))
                    .toList();

            return new AllocationPreviewResponse.StrategyResult(
                    strategy.name(), strategy.description(), true,
                    plan.splitShipment(), plan.shipmentCount(), plan.maxDistanceKm(),
                    segments, null);

        } catch (AllocationFailedException e) {
            return new AllocationPreviewResponse.StrategyResult(
                    strategy.name(), strategy.description(), false,
                    null, null, null, null, e.getMessage());
        }
    }
}
