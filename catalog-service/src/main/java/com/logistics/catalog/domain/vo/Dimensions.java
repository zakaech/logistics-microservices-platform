package com.logistics.catalog.domain.vo;

/** Physical envelope of a product, in millimetres and grams. Integers: no unit is fractional. */
public record Dimensions(
        Integer lengthMm,
        Integer widthMm,
        Integer heightMm,
        Integer weightG) {
}
