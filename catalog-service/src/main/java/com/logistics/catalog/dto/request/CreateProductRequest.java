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

        @NotBlank(message = "sku est obligatoire")
        @Pattern(regexp = "^[A-Z0-9-]{3,32}$",
                message = "sku doit comporter de 3 à 32 caractères parmi A-Z, 0-9 ou le tiret")
        String sku,

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
