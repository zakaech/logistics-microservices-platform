package com.logistics.order.exception;

import java.util.List;

/**
 * Raised when an order names a product the catalogue cannot serve.
 *
 * <p>422 rather than 404 or 400: the request is syntactically valid and the route exists, but the
 * content cannot be processed. Distinguishing "unknown" from "discontinued" matters to the
 * front-end, which should offer a search in one case and a replacement in the other.
 */
public class ProductUnavailableException extends RuntimeException {

    private final transient List<UnavailableProduct> products;

    public ProductUnavailableException(List<UnavailableProduct> products) {
        super("Some products are unknown or discontinued.");
        this.products = List.copyOf(products);
    }

    public List<UnavailableProduct> getProducts() {
        return products;
    }

    /** @param reason UNKNOWN or DISCONTINUED */
    public record UnavailableProduct(String productId, String reason) {

        public static UnavailableProduct unknown(String productId) {
            return new UnavailableProduct(productId, "UNKNOWN");
        }

        public static UnavailableProduct discontinued(String productId) {
            return new UnavailableProduct(productId, "DISCONTINUED");
        }
    }
}
