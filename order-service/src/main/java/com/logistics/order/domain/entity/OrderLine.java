package com.logistics.order.domain.entity;

import com.logistics.order.domain.vo.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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
import lombok.Setter;

import java.util.UUID;

/**
 * One product on an order, with the catalogue values frozen at the moment it was placed.
 *
 * <p>{@code productSku}, {@code productName} and {@code unitPrice} are a <b>snapshot</b>, not a
 * lookup. A product renamed or repriced next month must not rewrite what a customer agreed to and
 * paid; and an order must stay readable even if catalog-service is down or the product has since
 * been discontinued. The denormalisation is the point, not an optimisation.
 */
@Entity
@Table(name = "order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Logical reference to catalog_db.products._id. No foreign key: another service owns it. */
    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "product_sku", nullable = false, length = 32)
    private String productSku;

    @Column(name = "product_name", nullable = false, length = 160)
    private String productName;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false, length = 3))
    })
    private Money unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal lineTotal;

    /** Stored as well as computed, so a historical total can be audited without recomputation. */
    public Money computeLineTotal() {
        return unitPrice.multiply(quantity);
    }

    public void refreshLineTotal() {
        this.lineTotal = computeLineTotal().getAmount();
    }
}
