package com.logistics.inventory.repository;

import com.logistics.inventory.domain.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

/**
 * Read and append only.
 *
 * <p>There is deliberately no update or delete method: the ledger is the audit trail, and an audit
 * trail that can be rewritten is not one.
 */
public interface StockMovementRepository
        extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {

    /**
     * Filtered history.
     *
     * <p>Built from Specifications rather than a JPQL query with {@code :param IS NULL OR ...}
     * clauses: an untyped null parameter is bound as {@code bytea} on PostgreSQL and the comparison
     * fails at runtime. A specification simply omits the predicate it does not need.
     *
     * <p>The entity graph loads the stock item and its warehouse in the same query, so rendering a
     * page of movements does not fire one extra select per row.
     */
    @Override
    @EntityGraph(attributePaths = {"stockItem", "stockItem.warehouse"})
    Page<StockMovement> findAll(Specification<StockMovement> specification, Pageable pageable);

    List<StockMovement> findByReferenceOrderByOccurredAtAsc(String reference);
}
