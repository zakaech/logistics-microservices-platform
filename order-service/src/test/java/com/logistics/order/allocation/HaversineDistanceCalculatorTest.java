package com.logistics.order.allocation;

import com.logistics.order.allocation.distance.HaversineDistanceCalculator;
import com.logistics.order.domain.vo.GeoPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The distance formula, checked against values derived from the model rather than looked up.
 *
 * <p>One degree of latitude is, by definition of the mean-radius sphere the formula uses,
 * {@code R * pi / 180} kilometres. Asserting against that keeps the test honest: it verifies the
 * implementation against its own mathematics, not against a figure copied from somewhere.
 */
class HaversineDistanceCalculatorTest {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double ONE_DEGREE_KM = EARTH_RADIUS_KM * Math.PI / 180;
    private static final double TOLERANCE_KM = 0.001;

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    @DisplayName("the same point is zero kilometres away")
    void samePointIsZero() {
        GeoPoint point = GeoPoint.of(33.5731, -7.5898);
        assertThat(calculator.distanceKm(point, point)).isZero();
    }

    @Test
    @DisplayName("one degree of latitude equals R * pi / 180")
    void oneDegreeOfLatitude() {
        double distance = calculator.distanceKm(GeoPoint.of(0, 0), GeoPoint.of(1, 0));
        assertThat(distance).isCloseTo(ONE_DEGREE_KM, org.assertj.core.data.Offset.offset(TOLERANCE_KM));
    }

    @Test
    @DisplayName("one degree of longitude at the equator equals one degree of latitude")
    void oneDegreeOfLongitudeAtEquator() {
        double distance = calculator.distanceKm(GeoPoint.of(0, 0), GeoPoint.of(0, 1));
        assertThat(distance).isCloseTo(ONE_DEGREE_KM, org.assertj.core.data.Offset.offset(TOLERANCE_KM));
    }

    @Test
    @DisplayName("meridians converge: a degree of longitude is shorter away from the equator")
    void longitudeShrinksWithLatitude() {
        double atEquator = calculator.distanceKm(GeoPoint.of(0, 0), GeoPoint.of(0, 1));
        double atSixty = calculator.distanceKm(GeoPoint.of(60, 0), GeoPoint.of(60, 1));

        // cos(60 degrees) = 0.5, so the parallel is half as long. A flat Pythagorean
        // approximation would report the two as equal - which is why it is not used.
        assertThat(atSixty).isCloseTo(atEquator / 2, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    @DisplayName("distance is symmetric")
    void isSymmetric() {
        GeoPoint casablanca = GeoPoint.of(33.5731, -7.5898);
        GeoPoint rabat = GeoPoint.of(34.0209, -6.8416);

        assertThat(calculator.distanceKm(casablanca, rabat))
                .isEqualTo(calculator.distanceKm(rabat, casablanca));
    }

    @Test
    @DisplayName("antipodal points are half the circumference apart, without precision loss")
    void handlesAntipodalPoints() {
        double distance = calculator.distanceKm(GeoPoint.of(0, 0), GeoPoint.of(0, 180));

        // Half the great circle. The spherical law of cosines loses precision here; atan2 does not.
        assertThat(distance).isCloseTo(Math.PI * EARTH_RADIUS_KM,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("ordering is what the engine relies on: farther degrees mean farther kilometres")
    void isMonotonicInDegrees() {
        double oneDegree = calculator.distanceKm(GeoPoint.of(0, 0), GeoPoint.of(1, 0));
        double threeDegrees = calculator.distanceKm(GeoPoint.of(0, 0), GeoPoint.of(3, 0));
        double sixDegrees = calculator.distanceKm(GeoPoint.of(0, 0), GeoPoint.of(6, 0));

        assertThat(oneDegree).isLessThan(threeDegrees);
        assertThat(threeDegrees).isLessThan(sixDegrees);
    }
}
