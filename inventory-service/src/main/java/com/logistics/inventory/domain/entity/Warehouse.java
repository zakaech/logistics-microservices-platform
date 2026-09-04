package com.logistics.inventory.domain.entity;

import com.logistics.inventory.domain.vo.Address;
import com.logistics.inventory.domain.vo.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import java.util.UUID;

/**
 * A physical warehouse.
 *
 * <p>Carries geographic coordinates because the order allocation engine ranks candidate warehouses
 * by distance to the delivery address. Deactivating rather than deleting keeps the movement history
 * of a closed site readable.
 */
@Entity
@Table(name = "warehouses")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Human-readable business key, e.g. {@code WH-CASA-01}. */
    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Embedded
    private GeoPoint location;

    @Embedded
    private Address address;

    /** An inactive warehouse is excluded from availability and allocation, but keeps its history. */
    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
