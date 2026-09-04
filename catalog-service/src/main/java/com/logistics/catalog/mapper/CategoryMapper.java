package com.logistics.catalog.mapper;

import com.logistics.catalog.domain.document.Category;
import com.logistics.catalog.domain.vo.AttributeDefinition;
import com.logistics.catalog.dto.common.AttributeDefinitionDto;
import com.logistics.catalog.dto.response.CategoryResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Category to DTO translation, including assembling a flat list into a tree. */
@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return toResponse(category, null);
    }

    public List<AttributeDefinition> toAttributeSchema(List<AttributeDefinitionDto> dtos) {
        if (dtos == null) {
            return new ArrayList<>();
        }
        return dtos.stream()
                .map(dto -> new AttributeDefinition(dto.key(), dto.label(), dto.type(), dto.required()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Assembles a flat list into a tree in a single pass over the data.
     *
     * <p>The alternative - one query per level - would issue as many round trips as the tree is
     * deep. Here the caller fetches every category once and the shape is built in memory.
     */
    public List<CategoryResponse> toTree(List<Category> categories) {
        Map<String, List<Category>> byParent = categories.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? "" : c.getParentId()));

        return buildChildren(byParent, "");
    }

    private List<CategoryResponse> buildChildren(Map<String, List<Category>> byParent, String parentKey) {
        return byParent.getOrDefault(parentKey, List.of()).stream()
                .sorted(Comparator.comparing(Category::getName))
                .map(category -> toResponse(category, buildChildren(byParent, category.getId())))
                .toList();
    }

    private CategoryResponse toResponse(Category category, List<CategoryResponse> children) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getParentId(),
                category.getPath(),
                toAttributeSchemaDto(category.getAttributeSchema()),
                children,
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    private List<AttributeDefinitionDto> toAttributeSchemaDto(List<AttributeDefinition> schema) {
        if (schema == null) {
            return List.of();
        }
        return schema.stream()
                .map(a -> new AttributeDefinitionDto(a.key(), a.label(), a.type(), a.required()))
                .toList();
    }
}
