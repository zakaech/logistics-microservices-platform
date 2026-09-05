package com.logistics.order.domain.entity;

import com.logistics.order.domain.enums.OrderStatus;
import com.logistics.order.domain.vo.DeliveryAddress;
import com.logistics.order.domain.vo.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A customer order: the aggregate root.
 *
 * <p>The rules live here rather than in the service layer. {@link #transitionTo} is the only way the
 * status changes and it refuses an illegal move; {@link #applyAllocation} is the only way shipments
 * are attached. A service that could set the status directly would be a service that can, one day,
 * set it wrongly.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Public-facing identifier, and the idempotency key of the stock reservation. */
    @Column(name = "order_number", nullable = false, length = 20, updatable = false)
    private String orderNumber;

    /** Taken from the JWT subject, never from the request body. */
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false, length = 3))
    })
    private Money total;

    @Embedded
    private DeliveryAddress deliveryAddress;

    /** Name of the strategy that produced the plan, so a past decision can be explained. */
    @Column(name = "allocation_strategy", length = 40)
    private String allocationStrategy;

    @Builder.Default
    @Column(name = "split_shipment", nullable = false)
    private boolean splitShipment = false;

    /** Links to the inventory reservation, which is what compensation needs. */
    @Column(name = "reservation_reference", length = 64)
    private String reservationReference;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderAllocation> allocations = new ArrayList<>();

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public void addLine(OrderLine line) {
        line.setOrder(this);
        line.refreshLineTotal();
        lines.add(line);
    }

    /** Recomputed from the snapshot prices; a total sent by the client is never trusted. */
    public void recomputeTotal() {
        Money running = Money.zero(lines.isEmpty() ? "EUR" : lines.get(0).getUnitPrice().getCurrency());
        for (OrderLine line : lines) {
            running = running.add(line.computeLineTotal());
        }
        this.total = running;
    }

    public boolean isOwnedBy(UUID candidateCustomerId) {
        return customerId.equals(candidateCustomerId);
    }

    /**
     * Moves the order, refusing anything the state machine forbids.
     *
     * @throws com.logistics.order.exception.IllegalOrderStateException on an illegal move
     */
    public void transitionTo(OrderStatus target, String reason, String changedBy, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new com.logistics.order.exception.IllegalOrderStateException(
                    orderNumber, status, target);
        }
        statusHistory.add(OrderStatusHistory.builder()
                .order(this)
                .fromStatus(status)
                .toStatus(target)
                .reason(reason)
                .changedBy(changedBy)
                .changedAt(now)
                .build());
        this.status = target;
    }

    /** Records the first status, which has no predecessor and so is not a transition. */
    public void recordCreation(String changedBy, Instant now) {
        statusHistory.add(OrderStatusHistory.builder()
                .order(this)
                .fromStatus(null)
                .toStatus(status)
                .reason("Commande créée")
                .changedBy(changedBy)
                .changedAt(now)
                .build());
    }

    public void addAllocation(OrderAllocation allocation) {
        allocation.setOrder(this);
        allocations.add(allocation);
    }

    public OrderLine lineForProduct(String productId) {
        return lines.stream()
                .filter(line -> line.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Order " + orderNumber + " has no line for product " + productId + "."));
    }

    public BigDecimal totalAmount() {
        return total == null ? BigDecimal.ZERO : total.getAmount();
    }
}
