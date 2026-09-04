package com.logistics.inventory.repository.spec;

import com.logistics.inventory.domain.entity.StockMovement;
import com.logistics.inventory.domain.enums.MovementType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/**
 * Composable filters for the movement history.
 *
 * <p>Each method returns either a predicate or null; Spring Data drops the null ones when combining
 * with {@code and}. That is what lets an absent filter contribute nothing at all to the SQL,
 * instead of contributing a null comparison the database cannot type.
 */
public final class StockMovementSpecifications {

    private StockMovementSpecifications() {
    }

    public static Specification<StockMovement> inWarehouse(UUID warehouseId) {
        return warehouseId == null ? null
                : (root, query, cb) -> cb.equal(root.get("stockItem").get("warehouse").get("id"), warehouseId);
    }

    public static Specification<StockMovement> forProduct(String productId) {
        return (productId == null || productId.isBlank()) ? null
                : (root, query, cb) -> cb.equal(root.get("stockItem").get("productId"), productId);
    }

    public static Specification<StockMovement> ofType(MovementType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<StockMovement> occurredFrom(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
    }

    public static Specification<StockMovement> occurredUntil(Instant to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to);
    }

    public static Specification<StockMovement> withReference(String reference) {
        return (reference == null || reference.isBlank()) ? null
                : (root, query, cb) -> cb.equal(root.get("reference"), reference);
    }
}
