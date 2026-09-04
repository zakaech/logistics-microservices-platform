package com.logistics.order.allocation;

import com.logistics.order.allocation.distance.HaversineDistanceCalculator;
import com.logistics.order.allocation.impl.SingleShipmentAllocationStrategy;
import com.logistics.order.allocation.model.AllocationPlan;
import com.logistics.order.allocation.model.WarehouseCandidate;
import com.logistics.order.exception.AllocationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.logistics.order.allocation.AllocationFixture.P1;
import static com.logistics.order.allocation.AllocationFixture.P2;
import static com.logistics.order.allocation.AllocationFixture.line;
import static com.logistics.order.allocation.AllocationFixture.request;
import static com.logistics.order.allocation.AllocationFixture.unitsOf;
import static com.logistics.order.allocation.AllocationFixture.warehouse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** The strategy that trades kilometres for parcels. */
class SingleShipmentAllocationStrategyTest {

    private SingleShipmentAllocationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SingleShipmentAllocationStrategy(new HaversineDistanceCalculator());
    }

    @Nested
    @DisplayName("nominal case")
    class Nominal {

        @Test
        @DisplayName("prefers a single warehouse even when it is farther away")
        void prefersOneShipmentOverProximity() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 10), line(P2, 4)),
                    warehouse("WH-NEAR", 1, P1, 6, P2, 4),
                    warehouse("WH-FAR", 6, P1, 20, P2, 20)));

            // The whole point of the strategy: WH-NEAR is closer but cannot cover the order alone,
            // so the plan takes the one warehouse that can.
            assertThat(plan.segments()).hasSize(1);
            assertThat(plan.splitShipment()).isFalse();
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-FAR");
            assertThat(plan.strategyName()).isEqualTo("single-shipment");
        }

        @Test
        @DisplayName("among warehouses that can each cover the order, takes the closest")
        void closestAmongFullCoverers() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 5)),
                    warehouse("WH-FAR", 8, P1, 100),
                    warehouse("WH-MID", 3, P1, 100),
                    warehouse("WH-NEAR", 1, P1, 100)));

            assertThat(plan.segments()).hasSize(1);
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-NEAR");
        }
    }

    @Nested
    @DisplayName("insufficient stock")
    class InsufficientStock {

        @Test
        @DisplayName("refuses when the network holds too little, exactly like the other strategy")
        void refusesWhenNetworkCannotCover() {
            AllocationFailedException failure = catchThrowableOfType(
                    () -> strategy.allocate(request(
                            List.of(line(P1, 12)),
                            warehouse("WH-A", 1, P1, 5),
                            warehouse("WH-B", 2, P1, 4))),
                    AllocationFailedException.class);

            assertThat(failure.getUnsatisfied()).singleElement().satisfies(unsatisfied -> {
                assertThat(unsatisfied.requested()).isEqualTo(12);
                assertThat(unsatisfied.availableAcrossNetwork()).isEqualTo(9);
            });
        }
    }

    @Nested
    @DisplayName("split across warehouses")
    class Split {

        @Test
        @DisplayName("when no site can cover alone, uses the fewest warehouses, not the nearest")
        void minimisesShipmentCount() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 10)),
                    warehouse("WH-TINY-A", 1, P1, 2),
                    warehouse("WH-TINY-B", 2, P1, 2),
                    warehouse("WH-BULK", 7, P1, 8)));

            // Greedy set cover takes the deepest contributor first, then whatever completes the
            // order: two parcels instead of the three the distance-first rule would produce.
            assertThat(plan.segments()).hasSize(2);
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-BULK");
            assertThat(unitsOf(plan.segments().get(0), P1)).isEqualTo(8);
            assertThat(unitsOf(plan.segments().get(1), P1)).isEqualTo(2);
            assertThat(plan.totalUnits()).isEqualTo(10);
            assertThat(plan.splitShipment()).isTrue();
        }

        @Test
        @DisplayName("coverage is recomputed after each pick, not ranked once upfront")
        void coverageIsRecomputedBetweenPicks() {
            // WH-BOTH covers the most overall and is taken first. What remains is P2 only, so the
            // second pick must be judged on P2 alone - a static ranking would still prefer WH-P1,
            // which by then contributes nothing.
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 4), line(P2, 3)),
                    warehouse("WH-BOTH", 5, P1, 4, P2, 1),
                    warehouse("WH-P1", 1, P1, 10),
                    warehouse("WH-P2", 2, P2, 10)));

            assertThat(plan.totalUnits()).isEqualTo(7);
            assertThat(plan.segments()).hasSize(2);
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-BOTH");
            assertThat(plan.segments().get(1).warehouseCode()).isEqualTo("WH-P2");
            assertThat(unitsOf(plan.segments().get(1), P2)).isEqualTo(2);
        }

        @Test
        @DisplayName("splits across three when two cannot finish the order")
        void splitsAcrossThreeWhenNeeded() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 9)),
                    warehouse("WH-A", 1, P1, 3),
                    warehouse("WH-B", 2, P1, 3),
                    warehouse("WH-C", 3, P1, 3)));

            assertThat(plan.segments()).hasSize(3);
            assertThat(plan.totalUnits()).isEqualTo(9);
        }
    }

    @Nested
    @DisplayName("ties between warehouses")
    class Ties {

        @Test
        @DisplayName("two warehouses both able to cover the order: the closest wins")
        void tieOnCoverageBrokenByDistance() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 5)),
                    warehouse("WH-FAR", 4, P1, 50),
                    warehouse("WH-NEAR", 1, P1, 50)));

            assertThat(plan.segments()).hasSize(1);
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-NEAR");
        }

        @Test
        @DisplayName("equal coverage AND equal distance: the code decides, deterministically")
        void tieBrokenByCodeDeterministically() {
            WarehouseCandidate zulu = warehouse("WH-ZULU", 2, P1, 5);
            WarehouseCandidate alpha = warehouse("WH-ALPHA", 2, P1, 5);

            AllocationPlan first = strategy.allocate(request(List.of(line(P1, 5)), zulu, alpha));
            AllocationPlan second = strategy.allocate(request(List.of(line(P1, 5)), alpha, zulu));

            assertThat(first.segments().get(0).warehouseCode()).isEqualTo("WH-ALPHA");
            assertThat(second.segments().get(0).warehouseCode()).isEqualTo("WH-ALPHA");
        }

        @Test
        @DisplayName("a tie inside the greedy split is also resolved deterministically")
        void tieInSplitIsDeterministic() {
            WarehouseCandidate[] candidates = {
                    warehouse("WH-B", 2, P1, 3),
                    warehouse("WH-A", 2, P1, 3),
                    warehouse("WH-C", 2, P1, 3)};

            AllocationPlan first = strategy.allocate(request(List.of(line(P1, 9)), candidates));
            AllocationPlan second = strategy.allocate(request(List.of(line(P1, 9)), candidates));

            assertThat(first.segments()).extracting(s -> s.warehouseCode())
                    .isEqualTo(second.segments().stream().map(s -> s.warehouseCode()).toList());
            assertThat(first.segments().get(0).warehouseCode()).isEqualTo("WH-A");
        }
    }

    @Nested
    @DisplayName("the two strategies are genuinely interchangeable, and genuinely different")
    class ComparedWithNearest {

        @Test
        @DisplayName("same order, same stock, two defensible but different plans")
        void producesADifferentPlanFromNearest() {
            var nearest = new com.logistics.order.allocation.impl.NearestWarehouseAllocationStrategy(
                    new HaversineDistanceCalculator());

            WarehouseCandidate[] candidates = {
                    warehouse("WH-NEAR", 1, P1, 6),
                    warehouse("WH-FAR", 6, P1, 10)};
            List<com.logistics.order.allocation.model.RequestedLine> lines = List.of(line(P1, 10));

            AllocationPlan byDistance = nearest.allocate(request(lines, candidates));
            AllocationPlan bySingleShipment = strategy.allocate(request(lines, candidates));

            // Distance-first splits to use the closer site; single-shipment takes one parcel.
            assertThat(byDistance.shipmentCount()).isEqualTo(2);
            assertThat(bySingleShipment.shipmentCount()).isEqualTo(1);

            // Both are complete and correct: neither loses or invents a unit.
            assertThat(byDistance.totalUnits()).isEqualTo(10);
            assertThat(bySingleShipment.totalUnits()).isEqualTo(10);

            // Each is better on the axis it optimises: distance-first sends its first parcel from
            // the closest site, single-shipment sends one parcel from the site that has it all.
            assertThat(byDistance.segments().get(0).warehouseCode()).isEqualTo("WH-NEAR");
            assertThat(bySingleShipment.segments().get(0).warehouseCode()).isEqualTo("WH-FAR");
        }
    }
}
