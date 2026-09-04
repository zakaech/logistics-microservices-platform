package com.logistics.catalog.domain.document;

import com.logistics.catalog.domain.vo.AttributeDefinition;
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
import java.util.List;

/**
 * A catalogue category, which also declares the technical attributes its products must carry.
 *
 * <p>The hierarchy uses a <b>materialised path</b> ({@code handling/pallet-trucks}) rather than
 * parent pointers alone: the dominant query is "everything under this branch", which a prefix match
 * answers in one round trip, where a parent-pointer tree would need a recursive traversal.
 */
@Document(collection = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    public static final String PATH_SEPARATOR = "/";

    @Id
    private String id;

    private String name;

    /** URL-friendly identifier, unique across the whole catalogue. */
    private String slug;

    /** Null for a root category. */
    private String parentId;

    /** Slugs of every ancestor plus this one, joined by {@code /}. Derived from the tree. */
    private String path;

    @Builder.Default
    private List<AttributeDefinition> attributeSchema = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    /** Builds this category's path from its parent's. */
    public static String buildPath(String parentPath, String slug) {
        return (parentPath == null || parentPath.isBlank()) ? slug : parentPath + PATH_SEPARATOR + slug;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    /** True when {@code candidatePath} is this category or one of its descendants. */
    public boolean containsInSubtree(String candidatePath) {
        return candidatePath != null
                && (candidatePath.equals(path) || candidatePath.startsWith(path + PATH_SEPARATOR));
    }
}
