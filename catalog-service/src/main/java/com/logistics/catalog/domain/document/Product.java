package com.logistics.catalog.domain.document;

import com.logistics.catalog.domain.enums.ProductStatus;
import com.logistics.catalog.domain.vo.Dimensions;
import com.logistics.catalog.domain.vo.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A catalogue product.
 *
 * <p>{@code attributes} is the reason this context lives in MongoDB: a pallet truck has
 * {@code capacityKg} and {@code forkLengthMm}, a cardboard box has {@code flute} and
 * {@code burstStrengthKpa}. Relationally that becomes a wide sparse table, an EAV anti-pattern, or
 * one table per category. Here it is simply a nested object, validated against the category's
 * declared schema on write.
 */
@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private String id;

    /** Business key: human-readable, stable, unique. */
    private String sku;

    private String name;

    private String description;

    private String brand;

    private String categoryId;

    /**
     * Denormalised from the category, so a listing can show a breadcrumb without a second query.
     * Recomputed for every product of a subtree when a category is renamed or moved.
     */
    private String categoryPath;

    private Money price;

    /** Free-form technical sheet; keys are constrained by the category's attribute schema. */
    @Builder.Default
    private Map<String, String> attributes = new LinkedHashMap<>();

    private Dimensions dimensions;

    /** URLs only. Binary content does not belong in a document store. */
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }

    public void discontinue() {
        this.status = ProductStatus.DISCONTINUED;
    }
}
