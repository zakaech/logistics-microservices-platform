package com.logistics.order.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One shipment of an order: the engine's decision, persisted.
 *
 * <p>The plan is stored rather than recomputed on read, for three reasons. Availability moves, so
 * replaying the algorithm later would give a different answer; the warehouse code is kept so an
 * order can be displayed without calling inventory-service at all; and {@code distanceKm} records
 * the number the decision actually used, which is what makes a past choice explainable.
 */
@Entity
@Table(name = "order_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Logical reference to inventory_db.warehouses.id. */
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    /** Snapshot, so the order renders without a call to inventory-service. */
    @Column(name = "warehouse_code", nullable = false, length = 16)
    private String warehouseCode;

    @Column(name = "shipment_sequence", nullable = false)
    private int shipmentSequence;

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "allocation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderAllocationLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void addLine(OrderLine orderLine, int quantity) {
        lines.add(OrderAllocationLine.builder()
                .allocation(this)
                .orderLine(orderLine)
                .quantity(quantity)
                .build());
    }

    public long unitCount() {
        return lines.stream().mapToLong(OrderAllocationLine::getQuantity).sum();
    }
}
