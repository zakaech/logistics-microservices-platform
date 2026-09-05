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

        @NotBlank(message = "name est obligatoire")
        @Size(min = 3, max = 160, message = "name doit comporter entre 3 et 160 caractères")
        String name,

        @Size(max = 4000, message = "description ne doit pas dépasser 4000 caractères")
        String description,

        @Size(max = 80, message = "brand ne doit pas dépasser 80 caractères")
        String brand,

        @NotBlank(message = "categoryId est obligatoire")
        String categoryId,

        @NotNull(message = "price est obligatoire")
        @Valid MoneyDto price,

        Map<String, String> attributes,

        @Valid DimensionsDto dimensions,

        @Size(max = 10, message = "10 images au maximum")
        List<@Pattern(regexp = "^https?://.+", message = "chaque image doit être une URL http(s)") String> images) {
}
