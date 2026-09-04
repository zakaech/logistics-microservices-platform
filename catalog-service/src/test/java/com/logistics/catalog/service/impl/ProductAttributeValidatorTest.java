package com.logistics.catalog.service.impl;

import com.logistics.catalog.domain.document.Category;
import com.logistics.catalog.domain.enums.AttributeType;
import com.logistics.catalog.domain.vo.AttributeDefinition;
import com.logistics.catalog.exception.InvalidProductAttributesException;
import com.logistics.catalog.service.ProductAttributeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The rule that turns "schemaless" into "schema defined by data".
 *
 * <p>A pure unit test with no Spring and no MongoDB, because the rule itself is a pure function of
 * a map and a category schema.
 */
class ProductAttributeValidatorTest {

    private ProductAttributeValidator validator;
    private Category palletTrucks;

    @BeforeEach
    void setUp() {
        validator = new ProductAttributeValidator();
        palletTrucks = Category.builder()
                .id("cat-1")
                .name("Pallet trucks")
                .slug("pallet-trucks")
                .path("handling/pallet-trucks")
                .attributeSchema(List.of(
                        new AttributeDefinition("capacityKg", "Capacity (kg)", AttributeType.NUMBER, true),
                        new AttributeDefinition("forkLengthMm", "Fork length (mm)", AttributeType.NUMBER, true),
                        new AttributeDefinition("wheelMaterial", "Wheel material", AttributeType.STRING, false),
                        new AttributeDefinition("foldable", "Foldable", AttributeType.BOOLEAN, false)))
                .build();
    }

    @Test
    @DisplayName("accepts a sheet that satisfies the schema")
    void acceptsValidSheet() {
        Map<String, String> attributes = Map.of(
                "capacityKg", "2500",
                "forkLengthMm", "1150",
                "wheelMaterial", "polyurethane",
                "foldable", "false");

        assertThatCode(() -> validator.validate(attributes, palletTrucks)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("optional attributes may simply be absent")
    void optionalAttributesMayBeAbsent() {
        Map<String, String> attributes = Map.of("capacityKg", "2500", "forkLengthMm", "1150");
        assertThatCode(() -> validator.validate(attributes, palletTrucks)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a missing required attribute")
    void rejectsMissingRequired() {
        var exception = catchThrowableOfType(
                () -> validator.validate(Map.of("capacityKg", "2500"), palletTrucks),
                InvalidProductAttributesException.class);

        assertThat(exception.getViolations())
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.attribute()).isEqualTo("forkLengthMm");
                    assertThat(violation.reason()).isEqualTo("MISSING_REQUIRED");
                });
    }

    @Test
    @DisplayName("rejects an attribute the category does not declare - a typo must not be silently stored")
    void rejectsUnknownAttribute() {
        Map<String, String> withTypo = Map.of(
                "capacityKg", "2500", "forkLengthMm", "1150", "capacityKG", "2500");

        var exception = catchThrowableOfType(() -> validator.validate(withTypo, palletTrucks),
                InvalidProductAttributesException.class);

        assertThat(exception.getViolations())
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.attribute()).isEqualTo("capacityKG");
                    assertThat(violation.reason()).isEqualTo("UNKNOWN_ATTRIBUTE");
                });
    }

    @Test
    @DisplayName("rejects a value that does not match its declared type")
    void rejectsWrongType() {
        Map<String, String> attributes = Map.of(
                "capacityKg", "two thousand five hundred", "forkLengthMm", "1150");

        var exception = catchThrowableOfType(() -> validator.validate(attributes, palletTrucks),
                InvalidProductAttributesException.class);

        assertThat(exception.getViolations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.reason()).isEqualTo("INVALID_TYPE"));
    }

    @Test
    @DisplayName("reports every violation at once, so a form can be fixed in one round trip")
    void reportsAllViolationsTogether() {
        Map<String, String> attributes = Map.of("colour", "red", "foldable", "maybe");

        var exception = catchThrowableOfType(() -> validator.validate(attributes, palletTrucks),
                InvalidProductAttributesException.class);

        assertThat(exception.getViolations()).hasSize(4);
        assertThat(exception.getViolations()).extracting("reason")
                .containsExactlyInAnyOrder("MISSING_REQUIRED", "MISSING_REQUIRED",
                        "UNKNOWN_ATTRIBUTE", "INVALID_TYPE");
    }

    @Test
    @DisplayName("a category with no schema accepts nothing but an empty sheet")
    void categoryWithoutSchemaRejectsAnyAttribute() {
        Category bare = Category.builder().id("cat-2").slug("bare").path("bare")
                .attributeSchema(List.of()).build();

        assertThatCode(() -> validator.validate(Map.of(), bare)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(Map.of("anything", "1"), bare))
                .isInstanceOf(InvalidProductAttributesException.class);
    }

    @Test
    @DisplayName("normalisation trims values and drops blanks, so no empty-string noise is stored")
    void normalisationDropsBlanks() {
        Map<String, String> normalised = validator.normalise(new java.util.LinkedHashMap<>(Map.of(
                "capacityKg", "  2500  ", "wheelMaterial", "   ")));

        assertThat(normalised).containsExactly(Map.entry("capacityKg", "2500"));
    }
}
