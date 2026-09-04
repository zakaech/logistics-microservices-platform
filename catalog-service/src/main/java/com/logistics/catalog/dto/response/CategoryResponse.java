package com.logistics.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.logistics.catalog.dto.common.AttributeDefinitionDto;

import java.time.Instant;
import java.util.List;

/** A category. {@code children} is populated only when the tree view is requested. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryResponse(
        String id,
        String name,
        String slug,
        String parentId,
        String path,
        List<AttributeDefinitionDto> attributeSchema,
        List<CategoryResponse> children,
        Instant createdAt,
        Instant updatedAt) {
}
