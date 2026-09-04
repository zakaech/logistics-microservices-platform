package com.logistics.catalog.dto.request;

import com.logistics.catalog.dto.common.AttributeDefinitionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Full replacement of a category.
 *
 * <p>Changing {@code slug} or {@code parentId} moves the branch, so the service recomputes the
 * materialised path of every descendant and of every product underneath.
 */
public record UpdateCategoryRequest(

        @NotBlank(message = "name is required")
        @Size(min = 2, max = 80, message = "name must be between 2 and 80 characters")
        String name,

        @NotBlank(message = "slug is required")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "slug must be lowercase words separated by single hyphens")
        @Size(max = 60, message = "slug must not exceed 60 characters")
        String slug,

        String parentId,

        @Size(max = 30, message = "at most 30 attributes per category")
        List<@Valid AttributeDefinitionDto> attributeSchema) {
}
