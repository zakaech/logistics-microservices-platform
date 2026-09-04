package com.logistics.catalog.service;

import com.logistics.catalog.domain.document.Category;
import com.logistics.catalog.dto.request.CreateCategoryRequest;
import com.logistics.catalog.dto.request.UpdateCategoryRequest;
import com.logistics.catalog.dto.response.CategoryResponse;

import java.util.List;

/** Category tree management. */
public interface CategoryService {

    List<CategoryResponse> findAll(boolean asTree);

    CategoryResponse findById(String id);

    /** Loads a category or fails; used by the product service to reach the attribute schema. */
    Category requireById(String id);

    CategoryResponse create(CreateCategoryRequest request);

    /**
     * Replaces a category. Changing the slug or the parent moves the branch, so the materialised
     * path of every descendant and of every product underneath is recomputed.
     */
    CategoryResponse update(String id, UpdateCategoryRequest request);

    /** Refuses to delete a category that still has children or products. */
    void delete(String id);
}
