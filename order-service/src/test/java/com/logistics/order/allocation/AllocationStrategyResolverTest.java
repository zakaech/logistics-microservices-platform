package com.logistics.order.allocation;

import com.logistics.order.allocation.distance.HaversineDistanceCalculator;
import com.logistics.order.allocation.impl.NearestWarehouseAllocationStrategy;
import com.logistics.order.allocation.impl.SingleShipmentAllocationStrategy;
import com.logistics.order.config.AllocationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The registry that turns a strategy name into a strategy.
 *
 * <p>Small, but worth pinning: it is the piece that lets a third strategy be added without any
 * existing class changing, and the piece that decides what happens when a name is wrong.
 */
class AllocationStrategyResolverTest {

    private NearestWarehouseAllocationStrategy nearest;
    private SingleShipmentAllocationStrategy singleShipment;

    @BeforeEach
    void setUp() {
        HaversineDistanceCalculator distance = new HaversineDistanceCalculator();
        nearest = new NearestWarehouseAllocationStrategy(distance);
        singleShipment = new SingleShipmentAllocationStrategy(distance);
    }

    private AllocationStrategyResolver resolverWithDefault(String defaultStrategy) {
        return new AllocationStrategyResolver(
                List.of(nearest, singleShipment),
                new AllocationProperties(defaultStrategy, 300, 2));
    }

    @Test
    @DisplayName("registers every strategy on the classpath")
    void registersAllStrategies() {
        assertThat(resolverWithDefault("nearest").availableStrategies())
                .containsExactlyInAnyOrder("nearest", "single-shipment");
    }

    @Test
    @DisplayName("resolves by name")
    void resolvesByName() {
        AllocationStrategyResolver resolver = resolverWithDefault("nearest");

        assertThat(resolver.resolve("single-shipment")).isSameAs(singleShipment);
        assertThat(resolver.resolve("nearest")).isSameAs(nearest);
    }

    @Test
    @DisplayName("falls back to the configured default when no preference is expressed")
    void fallsBackToDefault() {
        AllocationStrategyResolver resolver = resolverWithDefault("single-shipment");

        assertThat(resolver.resolve(null)).isSameAs(singleShipment);
        assertThat(resolver.resolve("  ")).isSameAs(singleShipment);
    }

    @Test
    @DisplayName("an unknown name is refused and the alternatives are listed")
    void refusesUnknownName() {
        AllocationStrategyResolver resolver = resolverWithDefault("nearest");

        // Silently serving a different rule would leave an operator who mistyped wondering why the
        // plans look wrong.
        assertThatThrownBy(() -> resolver.resolve("nearset"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nearset")
                .hasMessageContaining("nearest")
                .hasMessageContaining("single-shipment");
    }

    @Test
    @DisplayName("a misconfigured default fails at startup, not on the first order")
    void rejectsUnknownDefaultAtStartup() {
        assertThatThrownBy(() -> resolverWithDefault("does-not-exist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    @DisplayName("every strategy describes itself, so a caller can choose knowingly")
    void strategiesAreSelfDescribing() {
        assertThat(resolverWithDefault("nearest").all())
                .allSatisfy(strategy -> {
                    assertThat(strategy.name()).isNotBlank();
                    assertThat(strategy.description()).isNotBlank();
                });
    }
}
