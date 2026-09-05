package com.logistics.inventory.domain.entity;

import com.logistics.inventory.domain.enums.ReservationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A hold on stock across one or more warehouses - the atomic step of the order saga.
 *
 * <p>{@code reference} is the order number and is UNIQUE. That constraint is the idempotency
 * guarantee: order-service can retry a reservation request after a timeout without any risk of
 * holding the stock twice.
 *
 * <p>{@code expiresAt} is the safety net. If the orchestrator crashes between reserving and
 * confirming, the sweeper releases the hold; without it, a crash would strand stock indefinitely.
 */
@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The order number. Unique, which is what makes reserving idempotent. */
    @Column(name = "reference", nullable = false, length = 64, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationLine> lines = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public void addLine(StockItem item, int quantity) {
        lines.add(ReservationLine.builder()
                .reservation(this)
                .stockItem(item)
                .quantity(quantity)
                .build());
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt.isBefore(now);
    }

    /**
     * Guards every state change. Confirming or cancelling anything but an ACTIVE reservation is a
     * conflict, not a no-op: silently accepting it would hide a double-confirm bug in the caller.
     */
    public void transitionTo(ReservationStatus target) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException(
                    "La réservation " + reference + " est " + status + " et ne peut plus changer.");
        }
        if (target == ReservationStatus.ACTIVE) {
            throw new IllegalStateException("A reservation cannot return to ACTIVE.");
        }
        this.status = target;
    }
}
