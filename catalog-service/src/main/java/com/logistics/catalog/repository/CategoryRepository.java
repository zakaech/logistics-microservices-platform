package com.logistics.catalog.repository;

import com.logistics.catalog.domain.document.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByParentId(String parentId);

    List<Category> findByParentId(String parentId);

    List<Category> findAllByOrderByPathAsc();

    /**
     * Every descendant of a branch, found with a single prefix match on the materialised path.
     * The regex is anchored and the separator appended so that {@code handling} does not match
     * {@code handling-tools}.
     */
    @Query("{ 'path': { $regex: ?0 } }")
    List<Category> findSubtreeByPathRegex(String anchoredPrefixRegex);
}
