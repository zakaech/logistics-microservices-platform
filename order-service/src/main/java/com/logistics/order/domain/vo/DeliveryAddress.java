package com.logistics.order.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Where the order goes.
 *
 * <p>Carries coordinates as well as a postal address because the allocation engine ranks warehouses
 * by distance to this point. Exposed to the engine as a {@link GeoPoint}, which is a plain record -
 * the engine never sees this JPA type.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DeliveryAddress {

    @Column(name = "delivery_line1", nullable = false, length = 180)
    private String line1;

    @Column(name = "delivery_city", nullable = false, length = 80)
    private String city;

    @Column(name = "delivery_postal_code", nullable = false, length = 16)
    private String postalCode;

    /** ISO-3166-1 alpha-2, as VARCHAR: CHAR would pad and break equality. */
    @Column(name = "delivery_country", nullable = false, length = 2)
    private String country;

    @Column(name = "delivery_latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "delivery_longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    public GeoPoint location() {
        return new GeoPoint(latitude, longitude);
    }
}
