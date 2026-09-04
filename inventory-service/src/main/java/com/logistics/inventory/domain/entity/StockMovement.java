package com.logistics.inventory.domain.entity;

import com.logistics.inventory.domain.enums.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the append-only stock ledger.
 *
 * <p>There is no setter and no update path: rows are written once and never touched again.
 * {@code quantityOnHand} is the projection of this history, which is what turns "the count is
 * wrong" into a question with an answer.
 *
 * <p>{@code quantity} is signed - positive adds, negative removes - so replaying the ledger is a
 * sum, not a switch on the type.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false, updatable = false)
    private StockItem stockItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private MovementType type;

    /** Signed: positive increases the affected quantity, negative decreases it. Never zero. */
    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    /** Order number, reservation id or supplier delivery note - whatever caused the movement. */
    @Column(name = "reference", length = 64, updatable = false)
    private String reference;

    /** User id or service name taken from the verified token. */
    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static StockMovement of(StockItem item, MovementType type, int signedQuantity,
                                   String reference, String createdBy, Instant occurredAt) {
        return StockMovement.builder()
                .stockItem(item)
                .type(type)
                .quantity(signedQuantity)
                .reference(reference)
                .createdBy(createdBy)
                .occurredAt(occurredAt)
                .build();
    }
}
