package com.logistics.catalog.mapper;

import com.logistics.catalog.domain.document.Product;
import com.logistics.catalog.domain.vo.Dimensions;
import com.logistics.catalog.domain.vo.Money;
import com.logistics.catalog.dto.common.DimensionsDto;
import com.logistics.catalog.dto.common.MoneyDto;
import com.logistics.catalog.dto.response.ProductResponse;
import com.logistics.catalog.dto.response.ProductSnapshotResponse;
import com.logistics.catalog.dto.response.ProductSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Document to DTO translation.
 *
 * <p>Hand-written rather than generated: two of these mappings compute something (the primary image
 * is the first of a list, and the three views deliberately expose different subsets) rather than
 * copying fields across. MapStruct would express that as {@code expression = "java(...)"} strings
 * inside annotations, which the compiler cannot check. auth-service keeps MapStruct because its
 * mapping really is a straight field copy - the rule is "generate the mechanical ones, write the
 * ones that decide something".
 */
@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getCategoryId(),
                product.getCategoryPath(),
                toMoneyDto(product.getPrice()),
                Map.copyOf(product.getAttributes()),
                toDimensionsDto(product.getDimensions()),
                List.copyOf(product.getImages()),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public ProductSummaryResponse toSummary(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getBrand(),
                product.getCategoryId(),
                product.getCategoryPath(),
                toMoneyDto(product.getPrice()),
                primaryImage(product),
                product.getStatus());
    }

    public ProductSnapshotResponse toSnapshot(Product product) {
        return new ProductSnapshotResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                toMoneyDto(product.getPrice()),
                product.getStatus());
    }

    public Money toMoney(MoneyDto dto) {
        return dto == null ? null : new Money(dto.amount(), dto.currency());
    }

    public Dimensions toDimensions(DimensionsDto dto) {
        return dto == null ? null
                : new Dimensions(dto.lengthMm(), dto.widthMm(), dto.heightMm(), dto.weightG());
    }

    private MoneyDto toMoneyDto(Money money) {
        return money == null ? null : new MoneyDto(money.amount(), money.currency());
    }

    private DimensionsDto toDimensionsDto(Dimensions dimensions) {
        return dimensions == null ? null
                : new DimensionsDto(dimensions.lengthMm(), dimensions.widthMm(),
                dimensions.heightMm(), dimensions.weightG());
    }

    private String primaryImage(Product product) {
        List<String> images = product.getImages();
        return (images == null || images.isEmpty()) ? null : images.get(0);
    }
}
