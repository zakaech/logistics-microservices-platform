package com.logistics.inventory.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A point on the globe.
 *
 * <p>{@code NUMERIC(9,6)} rather than a floating-point type: about 11 cm of precision, exactly
 * representable, and it is the input to the distance ranking the allocation engine will perform in
 * phase 4. Ranking that silently depends on binary rounding is ranking nobody can reproduce.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GeoPoint {

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;
}
