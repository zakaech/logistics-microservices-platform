package com.logistics.order.allocation;

import com.logistics.order.allocation.distance.HaversineDistanceCalculator;
import com.logistics.order.allocation.impl.NearestWarehouseAllocationStrategy;
import com.logistics.order.allocation.model.AllocationPlan;
import com.logistics.order.allocation.model.WarehouseCandidate;
import com.logistics.order.domain.vo.GeoPoint;
import com.logistics.order.exception.AllocationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.logistics.order.allocation.AllocationFixture.DESTINATION;
import static com.logistics.order.allocation.AllocationFixture.P1;
import static com.logistics.order.allocation.AllocationFixture.P2;
import static com.logistics.order.allocation.AllocationFixture.line;
import static com.logistics.order.allocation.AllocationFixture.request;
import static com.logistics.order.allocation.AllocationFixture.unitsOf;
import static com.logistics.order.allocation.AllocationFixture.warehouse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The distance-first strategy.
 *
 * <p>No Spring, no database, no mock: the engine is a pure function, so each scenario is stated as
 * data and asserted directly. That is the property the design of the engine exists to buy.
 */
class NearestWarehouseAllocationStrategyTest {

    private NearestWarehouseAllocationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new NearestWarehouseAllocationStrategy(new HaversineDistanceCalculator());
    }

    @Nested
    @DisplayName("nominal case")
    class Nominal {

        @Test
        @DisplayName("one warehouse holds everything: a single shipment from the nearest")
        void singleWarehouseCoversTheOrder() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 10), line(P2, 4)),
                    warehouse("WH-NEAR", 1, P1, 50, P2, 50),
                    warehouse("WH-FAR", 5, P1, 50, P2, 50)));

            assertThat(plan.segments()).hasSize(1);
            assertThat(plan.splitShipment()).isFalse();
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-NEAR");
            assertThat(unitsOf(plan.segments().get(0), P1)).isEqualTo(10);
            assertThat(unitsOf(plan.segments().get(0), P2)).isEqualTo(4);
            assertThat(plan.strategyName()).isEqualTo("nearest");
        }

        @Test
        @DisplayName("shipments are numbered from 1, in the order they are planned")
        void shipmentsAreSequenced() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 10)),
                    warehouse("WH-A", 1, P1, 4),
                    warehouse("WH-B", 2, P1, 6)));

            assertThat(plan.segments()).extracting(segment -> segment.shipmentSequence())
                    .containsExactly(1, 2);
        }

        @Test
        @DisplayName("empty and irrelevant warehouses never appear in the plan")
        void emptyWarehousesAreExcluded() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 5)),
                    warehouse("WH-EMPTY", 1),
                    warehouse("WH-OTHER-PRODUCT", 1, P2, 100),
                    warehouse("WH-STOCKED", 3, P1, 5)));

            assertThat(plan.segments()).hasSize(1);
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-STOCKED");
        }
    }

    @Nested
    @DisplayName("insufficient stock")
    class InsufficientStock {

        @Test
        @DisplayName("refuses when the whole network holds too little, and says by how much")
        void refusesWhenNetworkCannotCover() {
            AllocationFailedException failure = catchThrowableOfType(
                    () -> strategy.allocate(request(
                            List.of(line(P1, 10)),
                            warehouse("WH-A", 1, P1, 4),
                            warehouse("WH-B", 2, P1, 2))),
                    AllocationFailedException.class);

            assertThat(failure.getUnsatisfied()).singleElement().satisfies(unsatisfied -> {
                assertThat(unsatisfied.productId()).isEqualTo(P1);
                assertThat(unsatisfied.requested()).isEqualTo(10);
                // 4 + 2: measured across the network, not per warehouse.
                assertThat(unsatisfied.availableAcrossNetwork()).isEqualTo(6);
            });
        }

        @Test
        @DisplayName("reports every short line at once, not just the first")
        void reportsAllShortLines() {
            AllocationFailedException failure = catchThrowableOfType(
                    () -> strategy.allocate(request(
                            List.of(line(P1, 10), line(P2, 8)),
                            warehouse("WH-A", 1, P1, 1, P2, 2))),
                    AllocationFailedException.class);

            assertThat(failure.getUnsatisfied()).hasSize(2);
            assertThat(failure.getUnsatisfied()).extracting(u -> u.productId())
                    .containsExactlyInAnyOrder(P1, P2);
        }

        @Test
        @DisplayName("refuses when no warehouse stocks the product at all")
        void refusesWhenProductIsUnknownToEveryWarehouse() {
            AllocationFailedException failure = catchThrowableOfType(
                    () -> strategy.allocate(request(
                            List.of(line(P1, 1)),
                            warehouse("WH-A", 1, P2, 100))),
                    AllocationFailedException.class);

            assertThat(failure.getUnsatisfied()).singleElement()
                    .satisfies(u -> assertThat(u.availableAcrossNetwork()).isZero());
        }
    }

    @Nested
    @DisplayName("split across warehouses")
    class Split {

        @Test
        @DisplayName("splits when no single warehouse can cover the order")
        void splitsWhenNecessary() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 10)),
                    warehouse("WH-NEAR", 1, P1, 6),
                    warehouse("WH-FAR", 4, P1, 20)));

            assertThat(plan.splitShipment()).isTrue();
            assertThat(plan.segments()).hasSize(2);
            // Nearest first, then the remainder from further away.
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-NEAR");
            assertThat(unitsOf(plan.segments().get(0), P1)).isEqualTo(6);
            assertThat(plan.segments().get(1).warehouseCode()).isEqualTo("WH-FAR");
            assertThat(unitsOf(plan.segments().get(1), P1)).isEqualTo(4);
            assertThat(plan.totalUnits()).isEqualTo(10);
        }

        @Test
        @DisplayName("splits a multi-product order line by line")
        void splitsMultiProductOrder() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 10), line(P2, 4)),
                    warehouse("WH-NEAR", 1, P1, 6),
                    warehouse("WH-FAR", 4, P1, 10, P2, 10)));

            assertThat(plan.segments()).hasSize(2);
            assertThat(unitsOf(plan.segments().get(0), P1)).isEqualTo(6);
            assertThat(unitsOf(plan.segments().get(0), P2)).isZero();
            assertThat(unitsOf(plan.segments().get(1), P1)).isEqualTo(4);
            assertThat(unitsOf(plan.segments().get(1), P2)).isEqualTo(4);
        }

        @Test
        @DisplayName("uses three warehouses when two are not enough")
        void splitsAcrossThree() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 9)),
                    warehouse("WH-A", 1, P1, 3),
                    warehouse("WH-B", 2, P1, 3),
                    warehouse("WH-C", 3, P1, 3)));

            assertThat(plan.segments()).hasSize(3);
            assertThat(plan.totalUnits()).isEqualTo(9);
            assertThat(plan.segments()).extracting(segment -> segment.warehouseCode())
                    .containsExactly("WH-A", "WH-B", "WH-C");
        }

        @Test
        @DisplayName("stops as soon as the order is filled, however much stock lies beyond")
        void stopsOnceFilled() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 5)),
                    warehouse("WH-A", 1, P1, 5),
                    warehouse("WH-B", 2, P1, 100),
                    warehouse("WH-C", 3, P1, 100)));

            assertThat(plan.segments()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("ties between warehouses")
    class Ties {

        @Test
        @DisplayName("two equidistant warehouses: the one covering more of the order wins")
        void tieBrokenByCoverage() {
            AllocationPlan plan = strategy.allocate(request(
                    List.of(line(P1, 5)),
                    warehouse("WH-SMALL", 2, P1, 3),
                    warehouse("WH-BIG", 2, P1, 5)));

            // Same distance, so preferring the deeper stock avoids a pointless second shipment.
            assertThat(plan.segments()).hasSize(1);
            assertThat(plan.segments().get(0).warehouseCode()).isEqualTo("WH-BIG");
        }

        @Test
        @DisplayName("equal distance AND equal coverage: the code decides, deterministically")
        void tieBrokenByCodeDeterministically() {
            WarehouseCandidate zulu = warehouse("WH-ZULU", 2, P1, 5);
            WarehouseCandidate alpha = warehouse("WH-ALPHA", 2, P1, 5);

            // Same inputs, opposite declaration order: the plan must not depend on the order the
            // availability snapshot happened to arrive in.
            AllocationPlan first = strategy.allocate(request(List.of(line(P1, 5)), zulu, alpha));
            AllocationPlan second = strategy.allocate(request(List.of(line(P1, 5)), alpha, zulu));

            assertThat(first.segments().get(0).warehouseCode()).isEqualTo("WH-ALPHA");
            assertThat(second.segments().get(0).warehouseCode()).isEqualTo("WH-ALPHA");
        }

        @Test
        @DisplayName("a tie in a split is resolved the same way, run after run")
        void tiedSplitIsReproducible() {
            WarehouseCandidate[] candidates = {
                    warehouse("WH-A", 2, P1, 4),
                    warehouse("WH-B", 2, P1, 4),
                    warehouse("WH-C", 1, P1, 2)};

            AllocationPlan first = strategy.allocate(request(List.of(line(P1, 8)), candidates));
            AllocationPlan second = strategy.allocate(request(List.of(line(P1, 8)), candidates));

            assertThat(first.segments()).extracting(s -> s.warehouseCode())
                    .isEqualTo(second.segments().stream().map(s -> s.warehouseCode()).toList());
            assertThat(first.totalUnits()).isEqualTo(8);
        }
    }

    @Test
    @DisplayName("the distance recorded on a segment is the one the decision used")
    void segmentCarriesTheDistanceUsed() {
        AllocationPlan plan = strategy.allocate(request(
                List.of(line(P1, 1)),
                warehouse("WH-A", 1, P1, 1)));

        double expected = new HaversineDistanceCalculator()
                .distanceKm(GeoPoint.of(1.0, 0.0), DESTINATION);
        assertThat(plan.segments().get(0).distanceKm()).isEqualTo(expected);
    }
}
