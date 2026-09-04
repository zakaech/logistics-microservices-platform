package com.logistics.order.repository.spec;

import com.logistics.order.domain.entity.Order;
import com.logistics.order.domain.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/**
 * Composable filters for the order list.
 *
 * <p>Each method returns a predicate or null, and Spring Data drops the nulls. That is what avoids
 * the {@code :param IS NULL OR ...} pattern, which binds an untyped null on PostgreSQL and fails at
 * runtime - a mistake this project has already made once, in auth-service.
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> ownedBy(UUID customerId) {
        return customerId == null ? null
                : (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdFrom(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdUntil(Instant to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
