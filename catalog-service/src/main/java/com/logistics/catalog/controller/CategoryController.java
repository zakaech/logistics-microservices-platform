package com.logistics.catalog.controller;

import com.logistics.catalog.dto.request.CreateCategoryRequest;
import com.logistics.catalog.dto.request.UpdateCategoryRequest;
import com.logistics.catalog.dto.response.CategoryResponse;
import com.logistics.catalog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** Category endpoints. Reads are public; every write requires {@code ROLE_ADMIN}. */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category tree and attribute schemas")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List categories, flat or as a tree")
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean tree) {
        return categoryService.findAll(tree);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one category with its attribute schema")
    public CategoryResponse getById(@PathVariable String id) {
        return categoryService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse created = categoryService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/categories/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a category; moving it re-paths the whole subtree")
    public CategoryResponse update(@PathVariable String id,
                                   @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a category; refused while it has children or products")
    public void delete(@PathVariable String id) {
        categoryService.delete(id);
    }
}
