package com.logistics.catalog.controller;

import com.logistics.catalog.domain.enums.ProductStatus;
import com.logistics.catalog.dto.request.CreateProductRequest;
import com.logistics.catalog.dto.request.ProductBatchRequest;
import com.logistics.catalog.dto.request.UpdateProductRequest;
import com.logistics.catalog.dto.request.UpdateProductStatusRequest;
import com.logistics.catalog.dto.response.PagedResponse;
import com.logistics.catalog.dto.response.ProductResponse;
import com.logistics.catalog.dto.response.ProductSnapshotResponse;
import com.logistics.catalog.dto.response.ProductSummaryResponse;
import com.logistics.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

/**
 * Product endpoints.
 *
 * <p>Reads are public: a visitor browses the catalogue before signing in. Every write requires
 * {@code ROLE_ADMIN}. The batch lookup is restricted to {@code ROLE_SERVICE} because it exists for
 * order-service, not for browsers.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Catalogue browsing and product administration")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List, search and filter products")
    public PagedResponse<ProductSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // The page size is capped server-side: an unbounded size is a denial-of-service knob.
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "name"));

        return productService.search(q, categoryId, status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one product")
    public ProductResponse getById(@PathVariable String id) {
        return productService.findById(id);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    @Operation(summary = "Bulk lookup by id, used by order-service to build order lines")
    public List<ProductSnapshotResponse> batch(@Valid @RequestBody ProductBatchRequest request) {
        return productService.findBatch(request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a product")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/products/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a product")
    public ProductResponse update(@PathVariable String id,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or discontinue a product")
    public ProductResponse changeStatus(@PathVariable String id,
                                        @Valid @RequestBody UpdateProductStatusRequest request) {
        return productService.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft delete: the product becomes DISCONTINUED")
    public void delete(@PathVariable String id) {
        productService.delete(id);
    }
}
