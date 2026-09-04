package com.logistics.catalog.exception;

import java.util.List;

/**
 * Raised when a product's technical sheet does not satisfy its category's attribute schema.
 *
 * <p>Carries every violation at once rather than failing on the first: a client fixing a form
 * should see all the problems in one round trip.
 */
public class InvalidProductAttributesException extends RuntimeException {

    private final transient List<AttributeViolation> violations;

    public InvalidProductAttributesException(List<AttributeViolation> violations) {
        super("The product attributes do not match the category schema.");
        this.violations = List.copyOf(violations);
    }

    public List<AttributeViolation> getViolations() {
        return violations;
    }

    /**
     * @param attribute the offending attribute key
     * @param reason    machine-readable cause: MISSING_REQUIRED, UNKNOWN_ATTRIBUTE or INVALID_TYPE
     * @param message   human-readable explanation
     */
    public record AttributeViolation(String attribute, String reason, String message) {

        public static AttributeViolation missing(String key) {
            return new AttributeViolation(key, "MISSING_REQUIRED",
                    "Attribute '" + key + "' is required by the category schema.");
        }

        public static AttributeViolation unknown(String key) {
            return new AttributeViolation(key, "UNKNOWN_ATTRIBUTE",
                    "Attribute '" + key + "' is not declared by the category schema.");
        }

        public static AttributeViolation invalidType(String key, String expectedType, String value) {
            return new AttributeViolation(key, "INVALID_TYPE",
                    "Attribute '" + key + "' must be a " + expectedType + ", got '" + value + "'.");
        }
    }
}
