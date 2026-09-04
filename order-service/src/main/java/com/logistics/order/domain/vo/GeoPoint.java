package com.logistics.order.domain.vo;

import java.math.BigDecimal;

/**
 * A point on the globe.
 *
 * <p>A plain record with no persistence or framework annotation, deliberately: it is the one domain
 * type the allocation engine needs, and the engine must stay free of JPA, Spring and I/O so that
 * every allocation scenario is a plain unit test.
 */
public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {

    public GeoPoint {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("A GeoPoint needs both a latitude and a longitude.");
        }
    }

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    public double latitudeAsDouble() {
        return latitude.doubleValue();
    }

    public double longitudeAsDouble() {
        return longitude.doubleValue();
    }
}
