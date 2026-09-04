package com.logistics.catalog.service;

import com.logistics.catalog.domain.enums.ProductStatus;
import com.logistics.catalog.dto.request.CreateProductRequest;
import com.logistics.catalog.dto.request.ProductBatchRequest;
import com.logistics.catalog.dto.request.UpdateProductRequest;
import com.logistics.catalog.dto.response.PagedResponse;
import com.logistics.catalog.dto.response.ProductResponse;
import com.logistics.catalog.dto.response.ProductSnapshotResponse;
import com.logistics.catalog.dto.response.ProductSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Product catalogue use cases. */
public interface ProductService {

    /**
     * Listing with optional filters. A search term uses the text index; without one the query is a
     * plain indexed filter.
     */
    PagedResponse<ProductSummaryResponse> search(String term, String categoryId,
                                                 ProductStatus status, Pageable pageable);

    ProductResponse findById(String id);

    /** Bulk lookup for order-service. Unknown ids are simply absent from the result. */
    List<ProductSnapshotResponse> findBatch(ProductBatchRequest request);

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(String id, UpdateProductRequest request);

    ProductResponse changeStatus(String id, ProductStatus status);

    /** Soft delete: the product becomes DISCONTINUED so past orders keep a resolvable reference. */
    void delete(String id);
}
