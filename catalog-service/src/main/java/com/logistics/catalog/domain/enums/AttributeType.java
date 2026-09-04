package com.logistics.catalog.domain.enums;

import java.math.BigDecimal;

/**
 * The types a category may declare for a technical attribute.
 *
 * <p>Attribute values are stored as strings - that is what makes the document model flexible - so
 * the declared type is what gives them meaning, and each constant knows how to check a raw value.
 */
public enum AttributeType {

    STRING {
        @Override
        public boolean accepts(String value) {
            return value != null && !value.isBlank();
        }
    },

    NUMBER {
        @Override
        public boolean accepts(String value) {
            if (value == null || value.isBlank()) {
                return false;
            }
            try {
                new BigDecimal(value.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    },

    BOOLEAN {
        @Override
        public boolean accepts(String value) {
            return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        }
    };

    public abstract boolean accepts(String value);
}
