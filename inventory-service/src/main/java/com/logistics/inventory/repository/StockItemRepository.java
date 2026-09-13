package com.logistics.inventory.repository;

import com.logistics.inventory.domain.entity.StockItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    Optional<StockItem> findByWarehouseIdAndProductId(UUID warehouseId, String productId);

    boolean existsByWarehouseIdAndProductId(UUID warehouseId, String productId);

    Page<StockItem> findByWarehouseId(UUID warehouseId, Pageable pageable);

    Page<StockItem> findByProductId(String productId, Pageable pageable);

    /**
     * Availability snapshot for a set of products, restricted to active warehouses.
     *
     * <p>One query, joined to the warehouse, because the allocation engine needs stock <b>and</b>
     * coordinates together: a second round trip per warehouse would be the N+1 problem wearing an
     * HTTP costume.
     */
    @Query("""
            SELECT s FROM StockItem s
            JOIN FETCH s.warehouse w
            WHERE s.productId IN :productIds
              AND w.active = true
            """)
    List<StockItem> findAvailabilityFor(@Param("productIds") Collection<String> productIds);

    /**
     * Loads stock rows for update, taking a database write lock on each.
     *
     * <p><b>Two details carry the whole concurrency strategy.</b>
     *
     * <p>First, {@code PESSIMISTIC_WRITE} issues {@code SELECT ... FOR UPDATE}: a concurrent
     * transaction touching the same rows blocks here rather than reading a value that is about to
     * change. Stock is contended precisely when it matters - the moment the last units are being
     * taken - and that is exactly when optimistic locking degrades into a retry storm.
     *
     * <p>Second, {@code ORDER BY s.id} matters. Two orders touching the same two stock rows
     * in opposite orders would each hold what the other waits for, and the database would kill one
     * on a deadlock. Acquiring locks in a single global order - the primary key - makes that
     * deadlock impossible by construction rather than merely unlikely.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id IN :ids ORDER BY s.id")
    List<StockItem> lockAllByIdOrderedById(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT s FROM StockItem s
            WHERE s.warehouse.id = :warehouseId
              AND (s.quantityOnHand - s.quantityReserved) <= s.reorderThreshold
            """)
    Page<StockItem> findLowStockByWarehouse(@Param("warehouseId") UUID warehouseId, Pageable pageable);

    /**
     * Resolves (warehouse, product) pairs to stock row ids, WITHOUT loading the entities.
     *
     * <p>This indirection is deliberate and easy to get wrong. If the entity were loaded here
     * first, the later locking query would find it already in the persistence context and Hibernate
     * would hand back that cached instance rather than re-reading the row - so the lock would be
     * taken while the quantities in memory were still the stale pre-lock values. Fetching only ids
     * keeps the locking query the first and only read of the row inside the transaction.
     */
    @Query("""
            SELECT s.id AS id, s.warehouse.id AS warehouseId, s.productId AS productId
            FROM StockItem s
            WHERE s.warehouse.id IN :warehouseIds
              AND s.productId IN :productIds
            """)
    List<StockItemKey> findKeys(@Param("warehouseIds") Collection<UUID> warehouseIds,
                                @Param("productIds") Collection<String> productIds);

    /** Projection carrying just enough to address a stock row. */
    interface StockItemKey {
        UUID getId();

        UUID getWarehouseId();

        String getProductId();
    }

    long countByWarehouseId(UUID warehouseId);
}
