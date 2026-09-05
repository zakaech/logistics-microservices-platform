package com.logistics.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The stock of one product in one warehouse.
 *
 * <p>Two quantities, never three: {@code quantityOnHand} is what is physically present and
 * {@code quantityReserved} is what is already promised. The available quantity is <b>derived</b>,
 * because storing it would create a third number to keep consistent with the other two - and it is
 * the one that would drift.
 *
 * <p>The domain methods below are the only way these numbers change, and each one restates the
 * invariant the database also enforces. Belt and braces on purpose: the check constraint is the
 * guarantee, these guards are the readable error.
 */
@Entity
@Table(name = "stock_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Logical reference to catalog_db.products._id. No foreign key: it lives in another service. */
    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Builder.Default
    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand = 0;

    @Builder.Default
    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved = 0;

    @Builder.Default
    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic lock for the low-contention write paths (stock corrections, manual movements).
     * The reservation path does not rely on it: see ReservationServiceImpl for why it takes an
     * explicit row lock instead.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** What can still be promised to a new order. */
    public int availableQuantity() {
        return quantityOnHand - quantityReserved;
    }

    public boolean isLowStock() {
        return availableQuantity() <= reorderThreshold;
    }

    public boolean canReserve(int quantity) {
        return quantity > 0 && quantity <= availableQuantity();
    }

    /** Promises stock to an order. Does not touch what is physically present. */
    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("Réservation impossible de " + quantity + " unité(s) du produit "
                    + productId + " : seulement " + availableQuantity() + " disponible(s).");
        }
        this.quantityReserved += quantity;
    }

    /** Gives a promise back, on cancellation or expiry. */
    public void release(int quantity) {
        if (quantity <= 0 || quantity > quantityReserved) {
            throw new IllegalStateException("Libération impossible de " + quantity + " unité(s) du produit "
                    + productId + " : seulement " + quantityReserved + " réservée(s).");
        }
        this.quantityReserved -= quantity;
    }

    /**
     * Turns a promise into a shipment: the goods leave, so both numbers drop together. Doing this
     * in one method is what keeps the invariant true at every instant.
     */
    public void shipReserved(int quantity) {
        if (quantity <= 0 || quantity > quantityReserved) {
            throw new IllegalStateException("Expédition impossible de " + quantity + " unité(s) du produit "
                    + productId + " : seulement " + quantityReserved + " réservée(s).");
        }
        this.quantityReserved -= quantity;
        this.quantityOnHand -= quantity;
    }

    /** Goods received. */
    public void receive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité reçue doit être strictement positive.");
        }
        this.quantityOnHand += quantity;
    }

    /**
     * Stock count correction, in either direction.
     *
     * <p>Refuses to take on-hand below what is already reserved: that stock is promised, and a
     * correction that invalidates a promise is a decision for an operator, not a silent write.
     */
    public void adjust(int delta) {
        int target = this.quantityOnHand + delta;
        if (target < 0) {
            throw new IllegalStateException("L'ajustement rendrait le stock physique négatif pour le produit "
                    + productId + ".");
        }
        if (target < quantityReserved) {
            throw new IllegalStateException("L'ajustement laisserait " + quantityReserved
                    + " unité(s) réservée(s) du produit " + productId + " sans stock physique en contrepartie.");
        }
        this.quantityOnHand = target;
    }

    /** Absolute level, used when loading initial stock or applying a physical count. */
    public void setOnHandTo(int quantity) {
        adjust(quantity - this.quantityOnHand);
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
