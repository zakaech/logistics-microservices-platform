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
        super("Les attributs du produit ne correspondent pas au schéma de la catégorie.");
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
                    "L'attribut '" + key + "' est requis par le schéma de la catégorie.");
        }

        public static AttributeViolation unknown(String key) {
            return new AttributeViolation(key, "UNKNOWN_ATTRIBUTE",
                    "L'attribut '" + key + "' n'est pas déclaré par le schéma de la catégorie.");
        }

        public static AttributeViolation invalidType(String key, String expectedType, String value) {
            return new AttributeViolation(key, "INVALID_TYPE",
                    "L'attribut '" + key + "' doit être de type " + expectedType + ", reçu '" + value + "'.");
        }
    }
}
