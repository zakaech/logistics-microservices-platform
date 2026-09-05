package com.logistics.inventory.domain;

import com.logistics.inventory.domain.entity.StockItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stock invariants, tested where they live.
 *
 * <p>No Spring, no database: these are the rules the entity enforces on its own, and they are worth
 * pinning down separately from the locking strategy that protects them under concurrency.
 */
class StockItemTest {

    private StockItem item;

    @BeforeEach
    void setUp() {
        item = StockItem.builder()
                .productId("P1")
                .quantityOnHand(10)
                .quantityReserved(0)
                .reorderThreshold(2)
                .build();
    }

    @Test
    @DisplayName("available quantity is derived, never stored")
    void availableIsDerived() {
        item.reserve(4);
        assertThat(item.getQuantityOnHand()).isEqualTo(10);
        assertThat(item.getQuantityReserved()).isEqualTo(4);
        assertThat(item.availableQuantity()).isEqualTo(6);
    }

    @Nested
    @DisplayName("reserve")
    class Reserve {

        @Test
        @DisplayName("holds units without touching what is physically present")
        void reserveDoesNotMoveGoods() {
            item.reserve(3);
            assertThat(item.getQuantityOnHand()).isEqualTo(10);
            assertThat(item.getQuantityReserved()).isEqualTo(3);
        }

        @Test
        @DisplayName("refuses to promise more than is available")
        void refusesOverReservation() {
            item.reserve(8);
            assertThat(item.canReserve(3)).isFalse();
            assertThatThrownBy(() -> item.reserve(3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("seulement 2 disponible");
        }

        @Test
        @DisplayName("refuses a non-positive quantity")
        void refusesNonPositive() {
            assertThat(item.canReserve(0)).isFalse();
            assertThatThrownBy(() -> item.reserve(-1)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("shipping and releasing")
    class ShipAndRelease {

        @Test
        @DisplayName("shipping drops both quantities together, keeping the invariant true throughout")
        void shipDropsBoth() {
            item.reserve(4);
            item.shipReserved(4);

            assertThat(item.getQuantityOnHand()).isEqualTo(6);
            assertThat(item.getQuantityReserved()).isZero();
            assertThat(item.availableQuantity()).isEqualTo(6);
        }

        @Test
        @DisplayName("releasing gives units back without moving goods")
        void releaseGivesBack() {
            item.reserve(4);
            item.release(4);

            assertThat(item.getQuantityOnHand()).isEqualTo(10);
            assertThat(item.getQuantityReserved()).isZero();
        }

        @Test
        @DisplayName("cannot ship or release more than is reserved")
        void cannotExceedReserved() {
            item.reserve(2);
            assertThatThrownBy(() -> item.shipReserved(3)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> item.release(3)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("adjustments")
    class Adjustments {

        @Test
        @DisplayName("refuses to leave reserved units unbacked by physical stock")
        void refusesToStripReservedStock() {
            item.reserve(8);
            assertThatThrownBy(() -> item.adjust(-5))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sans stock physique");
        }

        @Test
        @DisplayName("refuses to make on-hand negative")
        void refusesNegativeOnHand() {
            assertThatThrownBy(() -> item.adjust(-11)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("an absolute level is expressed as a relative adjustment, so the guards apply")
        void absoluteLevelUsesTheSameGuards() {
            item.reserve(6);
            item.setOnHandTo(6);
            assertThat(item.getQuantityOnHand()).isEqualTo(6);

            assertThatThrownBy(() -> item.setOnHandTo(5)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("low stock is measured on what is available, not on what is on the shelf")
    void lowStockUsesAvailable() {
        assertThat(item.isLowStock()).isFalse();
        item.reserve(8);
        assertThat(item.isLowStock()).isTrue();
    }
}
