package com.logistics.catalog.service.impl;

import com.logistics.catalog.domain.document.Category;
import com.logistics.catalog.domain.document.Product;
import com.logistics.catalog.domain.enums.ProductStatus;
import com.logistics.catalog.dto.request.CreateProductRequest;
import com.logistics.catalog.dto.request.ProductBatchRequest;
import com.logistics.catalog.dto.request.UpdateProductRequest;
import com.logistics.catalog.dto.response.PagedResponse;
import com.logistics.catalog.dto.response.ProductResponse;
import com.logistics.catalog.dto.response.ProductSnapshotResponse;
import com.logistics.catalog.dto.response.ProductSummaryResponse;
import com.logistics.catalog.exception.DuplicateResourceException;
import com.logistics.catalog.exception.ResourceNotFoundException;
import com.logistics.catalog.mapper.ProductMapper;
import com.logistics.catalog.repository.ProductRepository;
import com.logistics.catalog.service.CategoryService;
import com.logistics.catalog.service.ProductAttributeValidator;
import com.logistics.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductAttributeValidator attributeValidator;
    private final ProductMapper productMapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public PagedResponse<ProductSummaryResponse> search(String term, String categoryId,
                                                        ProductStatus status, Pageable pageable) {
        Query query = new Query();

        if (term != null && !term.isBlank()) {
            // Uses the text index over name, brand and description.
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(term.trim()));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            // Matches the branch, not just the exact category, via the materialised path.
            Category category = categoryService.requireById(categoryId);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("categoryId").is(categoryId),
                    Criteria.where("categoryPath").regex("^" + Pattern.quote(category.getPath()) + "(/|$)")));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }

        // Counted BEFORE with(pageable) is applied: that call mutates the query by adding skip
        // and limit, which would make the total the size of the current page.
        long total = mongoTemplate.count(query, Product.class);
        List<Product> products = mongoTemplate.find(query.with(pageable), Product.class);

        Page<Product> page = new PageImpl<>(products, pageable, total);
        return PagedResponse.from(page, productMapper::toSummary);
    }

    @Override
    public ProductResponse findById(String id) {
        return productMapper.toResponse(requireById(id));
    }

    @Override
    public List<ProductSnapshotResponse> findBatch(ProductBatchRequest request) {
        // Unknown ids are simply absent. The caller compares what it asked for with what it got and
        // decides what a missing product means for its own use case; guessing here would be wrong.
        return productRepository.findByIdIn(request.productIds()).stream()
                .map(productMapper::toSnapshot)
                .toList();
    }

    @Override
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Produit", "sku", request.sku());
        }

        Category category = categoryService.requireById(request.categoryId());
        attributeValidator.validate(request.attributes(), category);

        Product product = Product.builder()
                .sku(request.sku())
                .name(request.name().trim())
                .description(request.description())
                .brand(request.brand())
                .categoryId(category.getId())
                .categoryPath(category.getPath())
                .price(productMapper.toMoney(request.price()))
                .attributes(attributeValidator.normalise(request.attributes()))
                .dimensions(productMapper.toDimensions(request.dimensions()))
                .images(request.images() == null ? List.of() : List.copyOf(request.images()))
                .status(ProductStatus.ACTIVE)
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product {} (sku {})", saved.getId(), saved.getSku());
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse update(String id, UpdateProductRequest request) {
        Product product = requireById(id);

        Category category = categoryService.requireById(request.categoryId());
        // Re-validated against the TARGET category: moving a product to another category can make a
        // previously valid technical sheet invalid, and that must be caught here, not on read.
        attributeValidator.validate(request.attributes(), category);

        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setBrand(request.brand());
        product.setCategoryId(category.getId());
        product.setCategoryPath(category.getPath());
        product.setPrice(productMapper.toMoney(request.price()));
        product.setAttributes(attributeValidator.normalise(request.attributes()));
        product.setDimensions(productMapper.toDimensions(request.dimensions()));
        product.setImages(request.images() == null ? List.of() : List.copyOf(request.images()));

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    public ProductResponse changeStatus(String id, ProductStatus status) {
        Product product = requireById(id);
        product.setStatus(status);
        log.info("Product {} is now {}", id, status);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    public void delete(String id) {
        Product product = requireById(id);
        product.discontinue();
        productRepository.save(product);
        log.info("Product {} discontinued (soft delete)", id);
    }

    private Product requireById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", id));
    }
}
