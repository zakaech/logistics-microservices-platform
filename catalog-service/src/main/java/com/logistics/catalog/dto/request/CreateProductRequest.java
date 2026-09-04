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
 * Creation payload.
 *
 * <p>{@code attributes} is intentionally untyped here: Bean Validation cannot express "must match
 * the schema of the category this product belongs to", because that rule depends on another
 * document. It is enforced in the service by ProductAttributeValidator, which is why an attribute
 * error is a 422 and not a 400.
 */
public record CreateProductRequest(

        @NotBlank(message = "sku is required")
        @Pattern(regexp = "^[A-Z0-9-]{3,32}$",
                message = "sku must be 3 to 32 characters of A-Z, 0-9 or hyphen")
        String sku,

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
