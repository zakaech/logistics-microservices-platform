package com.logistics.catalog.config;

import com.logistics.catalog.domain.document.Category;
import com.logistics.catalog.domain.document.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

/**
 * Creates the collection indexes explicitly at startup.
 *
 * <p>Spring Boot disables automatic index creation by default, and that default is right: an index
 * built implicitly from an annotation is invisible in review and can lock a large collection at an
 * unpredictable moment. Declaring them here makes the set of indexes a reviewable list.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        var products = mongoTemplate.indexOps(Product.class);

        // The business key. Unique, so a duplicate SKU is refused by the engine even if two
        // requests race past the application-level check.
        products.ensureIndex(new Index().on("sku", Sort.Direction.ASC).unique().named("ux_products_sku"));

        // The catalogue listing filters on status and category together.
        products.ensureIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .on("categoryId", Sort.Direction.ASC)
                .named("ix_products_status_category"));

        // Subtree browsing uses a prefix match on the denormalised path.
        products.ensureIndex(new Index().on("categoryPath", Sort.Direction.ASC)
                .named("ix_products_category_path"));

        // Full-text search over the fields a shopper actually types into.
        products.ensureIndex(TextIndexDefinition.builder()
                .onField("name", 3F)
                .onField("brand", 2F)
                .onField("description")
                .named("tx_products_search")
                .build());

        var categories = mongoTemplate.indexOps(Category.class);
        categories.ensureIndex(new Index().on("slug", Sort.Direction.ASC).unique().named("ux_categories_slug"));
        categories.ensureIndex(new Index().on("parentId", Sort.Direction.ASC).named("ix_categories_parent"));
        categories.ensureIndex(new Index().on("path", Sort.Direction.ASC).named("ix_categories_path"));

        log.info("MongoDB indexes ensured for products and categories");
    }
}
