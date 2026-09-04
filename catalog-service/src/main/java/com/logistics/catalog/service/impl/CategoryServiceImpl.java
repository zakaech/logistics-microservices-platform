package com.logistics.catalog.service.impl;

import com.logistics.catalog.domain.document.Category;
import com.logistics.catalog.domain.document.Product;
import com.logistics.catalog.dto.request.CreateCategoryRequest;
import com.logistics.catalog.dto.request.UpdateCategoryRequest;
import com.logistics.catalog.dto.response.CategoryResponse;
import com.logistics.catalog.exception.CategoryNotEmptyException;
import com.logistics.catalog.exception.DuplicateResourceException;
import com.logistics.catalog.exception.ResourceNotFoundException;
import com.logistics.catalog.mapper.CategoryMapper;
import com.logistics.catalog.repository.CategoryRepository;
import com.logistics.catalog.repository.ProductRepository;
import com.logistics.catalog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryResponse> findAll(boolean asTree) {
        List<Category> categories = categoryRepository.findAllByOrderByPathAsc();
        return asTree
                ? categoryMapper.toTree(categories)
                : categories.stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    public CategoryResponse findById(String id) {
        return categoryMapper.toResponse(requireById(id));
    }

    @Override
    public Category requireById(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Override
    public CategoryResponse create(CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Category", "slug", request.slug());
        }

        String parentPath = resolveParentPath(request.parentId());

        Category category = Category.builder()
                .name(request.name().trim())
                .slug(request.slug())
                .parentId(request.parentId())
                .path(Category.buildPath(parentPath, request.slug()))
                .attributeSchema(categoryMapper.toAttributeSchema(request.attributeSchema()))
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Created category {} at path '{}'", saved.getId(), saved.getPath());
        return categoryMapper.toResponse(saved);
    }

    @Override
    public CategoryResponse update(String id, UpdateCategoryRequest request) {
        Category category = requireById(id);

        categoryRepository.findBySlug(request.slug())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Category", "slug", request.slug());
                });

        rejectCycle(category, request.parentId());

        String previousPath = category.getPath();
        String newPath = Category.buildPath(resolveParentPath(request.parentId()), request.slug());

        category.setName(request.name().trim());
        category.setSlug(request.slug());
        category.setParentId(request.parentId());
        category.setPath(newPath);
        category.setAttributeSchema(categoryMapper.toAttributeSchema(request.attributeSchema()));

        Category saved = categoryRepository.save(category);

        if (!newPath.equals(previousPath)) {
            repathSubtree(previousPath, newPath);
        }
        return categoryMapper.toResponse(saved);
    }

    @Override
    public void delete(String id) {
        Category category = requireById(id);

        // Cascading would silently delete or orphan products; refusing makes the caller decide.
        if (categoryRepository.existsByParentId(id)) {
            throw new CategoryNotEmptyException(id, "it still has child categories");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new CategoryNotEmptyException(id, "products still reference it");
        }

        categoryRepository.delete(category);
        log.info("Deleted category {}", id);
    }

    private String resolveParentPath(String parentId) {
        return parentId == null ? null : requireById(parentId).getPath();
    }

    /** A category cannot become its own descendant: that would detach the branch from the tree. */
    private void rejectCycle(Category category, String newParentId) {
        if (newParentId == null) {
            return;
        }
        if (newParentId.equals(category.getId())) {
            throw new IllegalArgumentException("A category cannot be its own parent.");
        }
        Category newParent = requireById(newParentId);
        if (category.containsInSubtree(newParent.getPath())) {
            throw new IllegalArgumentException(
                    "A category cannot be moved under one of its own descendants.");
        }
    }

    /**
     * Rewrites the materialised path of every descendant category and every product underneath.
     *
     * <p>The denormalised path is what makes a breadcrumb free to display; the price of that is
     * this propagation on the rare occasion a branch is renamed or moved. Done synchronously
     * because a catalogue is small and a half-updated tree is worse than a slow request.
     */
    private void repathSubtree(String previousPath, String newPath) {
        String anchoredPrefix = "^" + Pattern.quote(previousPath) + "(/|$)";

        List<Category> descendants = categoryRepository.findSubtreeByPathRegex(anchoredPrefix).stream()
                .filter(descendant -> !descendant.getPath().equals(newPath))
                .toList();

        for (Category descendant : descendants) {
            descendant.setPath(newPath + descendant.getPath().substring(previousPath.length()));
        }
        if (!descendants.isEmpty()) {
            categoryRepository.saveAll(descendants);
        }

        List<Product> affected = productRepository.findAll().stream()
                .filter(product -> product.getCategoryPath() != null)
                .filter(product -> product.getCategoryPath().equals(previousPath)
                        || product.getCategoryPath().startsWith(previousPath + Category.PATH_SEPARATOR))
                .toList();

        for (Product product : affected) {
            product.setCategoryPath(newPath + product.getCategoryPath().substring(previousPath.length()));
        }
        if (!affected.isEmpty()) {
            productRepository.saveAll(affected);
        }

        log.info("Re-pathed '{}' to '{}': {} categories, {} products",
                previousPath, newPath, descendants.size(), affected.size());
    }
}
