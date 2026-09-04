package com.logistics.order.service;

/**
 * Produces the public-facing order number.
 *
 * <p>An interface because the format is a business decision, not a technical one, and because the
 * database sequence behind the default implementation is exactly the kind of thing a test should
 * not need.
 */
public interface OrderNumberGenerator {

    /** e.g. {@code ORD-2026-000123}. Unique, and stable once assigned. */
    String next();
}
