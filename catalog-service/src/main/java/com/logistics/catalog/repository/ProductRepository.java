package com.logistics.catalog.repository;

import com.logistics.catalog.domain.document.Product;
import com.logistics.catalog.domain.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {

    boolean existsBySku(String sku);

    boolean existsByCategoryId(String categoryId);

    List<Product> findByCategoryId(String categoryId);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByCategoryId(String categoryId, Pageable pageable);

    Page<Product> findByStatusAndCategoryId(ProductStatus status, String categoryId, Pageable pageable);

    List<Product> findByIdIn(Collection<String> ids);
}
