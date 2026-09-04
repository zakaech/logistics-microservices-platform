package com.logistics.order.allocation.distance;

import com.logistics.order.domain.vo.GeoPoint;

/**
 * How far apart two points are.
 *
 * <p>An interface for one implementation today, which is not over-engineering here: distance is the
 * single input most likely to change. Great-circle distance is a proxy for what actually matters -
 * road distance, or carrier transit time - and swapping in a routing API later must not touch the
 * allocation strategies at all. It is also what lets a test inject a fixed distance and assert on
 * ordering alone.
 */
public interface DistanceCalculator {

    /** Distance in kilometres. Symmetric, and zero for identical points. */
    double distanceKm(GeoPoint from, GeoPoint to);
}
