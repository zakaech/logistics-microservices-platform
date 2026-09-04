package com.logistics.order.allocation.distance;

import com.logistics.order.domain.vo.GeoPoint;
import org.springframework.stereotype.Component;

/**
 * Great-circle distance by the haversine formula.
 *
 * <p>Chosen over the naive Pythagorean approximation on latitude/longitude, which is badly wrong
 * over the distances that separate warehouses, and over Vincenty, whose ellipsoidal precision buys
 * nothing when the answer only has to <em>rank</em> a handful of sites.
 *
 * <p>The formula is numerically stable near antipodal points, unlike the spherical law of cosines,
 * which loses precision to floating-point cancellation for small angles.
 */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    /** Mean Earth radius (IUGG), in kilometres. */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    @Override
    public double distanceKm(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitudeAsDouble());
        double lat2 = Math.toRadians(to.latitudeAsDouble());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(to.longitudeAsDouble() - from.longitudeAsDouble());

        double sinHalfLat = Math.sin(deltaLat / 2);
        double sinHalfLon = Math.sin(deltaLon / 2);

        double a = sinHalfLat * sinHalfLat
                + Math.cos(lat1) * Math.cos(lat2) * sinHalfLon * sinHalfLon;

        // atan2 keeps the result stable as `a` approaches 1; asin(sqrt(a)) would not.
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
