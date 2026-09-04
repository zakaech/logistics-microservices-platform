package com.logistics.catalog.dto.request;

import com.logistics.catalog.dto.common.DimensionsDto;
import com.logistics.catalog.dto.common.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Full replacement of a product.
 *
 * <p>The SKU is absent on purpose: it is the business key, printed on labels and referenced by past
 * orders, so it is set once at creation and never edited.
 */
public record UpdateProductRequest(

        @NotBlank(message = "name is required")
        @Size(min = 3, max = 160, message = "name must be between 3 and 160 characters")
        String name,

        @Size(max = 4000, message = "description must not exceed 4000 characters")
        String description,

        @Size(max = 80, message = "brand must not exceed 80 characters")
        String brand,

        @NotBlank(message = "categoryId is required")
        String categoryId,

        @NotNull(message = "price is required")
        @Valid MoneyDto price,

        Map<String, String> attributes,

        @Valid DimensionsDto dimensions,

        @Size(max = 10, message = "at most 10 images are allowed")
        List<@Pattern(regexp = "^https?://.+", message = "each image must be an http(s) URL") String> images) {
}
