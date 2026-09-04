package com.logistics.order.repository;

import com.logistics.order.domain.entity.Order;
import com.logistics.order.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * Loads an order with its lines join-fetched.
     *
     * <p>Only ONE collection is fetched here. Hibernate refuses to join-fetch two list-valued
     * associations at once (MultipleBagFetchException): the SQL product of two collections cannot
     * be un-flattened into rows unambiguously. The allocations and their lines are loaded instead
     * through {@code @BatchSize} on the entity, which turns what would be an N+1 into a small,
     * bounded number of queries.
     */
    @EntityGraph(attributePaths = {"lines"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"statusHistory"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithHistory(@Param("id") UUID id);

    /**
     * Filtered search; the specification omits the predicates the caller did not ask for.
     *
     * <p>Deliberately no entity graph. Join-fetching a collection alongside a Pageable makes
     * Hibernate paginate <em>in memory</em> - it loads every matching row and then slices, which
     * silently turns a paged endpoint into a full table scan. {@code @BatchSize} covers the lines
     * instead, in a bounded number of queries.
     */
    @Override
    Page<Order> findAll(Specification<Order> specification, Pageable pageable);

    long countByStatus(OrderStatus status);
}
