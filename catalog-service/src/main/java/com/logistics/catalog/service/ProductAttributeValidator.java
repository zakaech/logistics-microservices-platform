package com.logistics.catalog.service;

import com.logistics.catalog.domain.document.Category;
import com.logistics.catalog.domain.vo.AttributeDefinition;
import com.logistics.catalog.exception.InvalidProductAttributesException;
import com.logistics.catalog.exception.InvalidProductAttributesException.AttributeViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a product technical sheet against the schema declared by its category.
 *
 * <p>This is what makes the document model a design decision rather than an absence of one: the
 * structure is not fixed by a table definition, but it is not unconstrained either - the category
 * owns the contract and this class enforces it.
 *
 * <p>Validation is <b>strict</b>: an attribute the category does not declare is rejected. The
 * lenient alternative was considered and refused, because tolerating unknown keys makes the schema
 * decorative - a typo like {@code capacityKG} would be stored and silently disappear from
 * every filter and comparison built on {@code capacityKg}.
 *
 * <p>No I/O, no framework: the whole rule is a pure function of a map and a schema, so every case
 * below is a plain unit test.
 */
@Component
public class ProductAttributeValidator {

    /**
     * @throws InvalidProductAttributesException listing every violation at once, so a client fixing
     *                                           a form sees all the problems in one round trip
     */
    public void validate(Map<String, String> attributes, Category category) {
        Map<String, String> provided = attributes == null ? Map.of() : attributes;
        List<AttributeDefinition> schema = category.getAttributeSchema() == null
                ? List.of() : category.getAttributeSchema();

        Map<String, AttributeDefinition> declared = schema.stream()
                .collect(LinkedHashMap::new, (m, a) -> m.put(a.key(), a), Map::putAll);

        List<AttributeViolation> violations = new ArrayList<>();

        // Required attributes must be present and non-blank.
        declared.values().stream()
                .filter(AttributeDefinition::required)
                .filter(definition -> isBlank(provided.get(definition.key())))
                .map(definition -> AttributeViolation.missing(definition.key()))
                .forEach(violations::add);

        provided.forEach((key, value) -> {
            AttributeDefinition definition = declared.get(key);
            if (definition == null) {
                violations.add(AttributeViolation.unknown(key));
                return;
            }
            // An optional attribute may be omitted, but not present-and-malformed.
            if (!isBlank(value) && !definition.type().accepts(value)) {
                violations.add(AttributeViolation.invalidType(key, definition.type().name(), value));
            }
        });

        if (!violations.isEmpty()) {
            throw new InvalidProductAttributesException(violations);
        }
    }

    /** Drops blank optional values so the stored document has no empty-string noise. */
    public Map<String, String> normalise(Map<String, String> attributes) {
        if (attributes == null) {
            return new LinkedHashMap<>();
        }
        return attributes.entrySet().stream()
                .filter(entry -> !isBlank(entry.getValue()))
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue().trim()),
                        Map::putAll);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
