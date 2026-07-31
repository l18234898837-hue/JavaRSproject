package com.htcloud.orbit;

import org.hipparchus.geometry.partitioning.RegionFactory;
import org.hipparchus.geometry.spherical.twod.S2Point;
import org.hipparchus.geometry.spherical.twod.Sphere2D;
import org.hipparchus.geometry.spherical.twod.SphericalPolygonsSet;
import org.hipparchus.util.FastMath;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;

/**
 * Polygon/MultiPolygon到SphericalPolygonsSet的转换，源坐标系为WGS84
 */
public class SphericalPolygonSetFactory {
    private static double TOLERANCE = 1e-10;

    public static SphericalPolygonsSet build(Polygon polygon) {
        LinearRing exteriorRing = polygon.getExteriorRing();
        if (exteriorRing == null) {
            return null;
        }
        SphericalPolygonsSet result = build(exteriorRing);
        if (polygon.getNumInteriorRing() == 0) {
            return result;
        }
        RegionFactory<Sphere2D> regionFactory = new RegionFactory<>();
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            LinearRing interiorRing = polygon.getInteriorRingN(i);
            SphericalPolygonsSet s = build(interiorRing);
            result = (SphericalPolygonsSet) regionFactory.difference(result, s);
        }
        return result;
    }

    public static SphericalPolygonsSet build(LinearRing linearRing) {
        CoordinateSequence coordinateSequence = linearRing.getCoordinateSequence();
        boolean isCounterClockwise = Orientation.isCCW(coordinateSequence);
        S2Point[] points = new S2Point[coordinateSequence.size()];
        for (int i = 0; i < coordinateSequence.size(); i++) {
            // SphericalPolygonsSet 的顶点要逆时针序的
            if (isCounterClockwise) {
                points[i] = buildS2Point(coordinateSequence.getCoordinate(i));
            } else {
                points[coordinateSequence.size() - 1 - i] = buildS2Point(coordinateSequence.getCoordinate(i));
            }
        }
        return new SphericalPolygonsSet(TOLERANCE, points);
    }

    public static SphericalPolygonsSet build(MultiPolygon multiPolygon) {
        RegionFactory<Sphere2D> regionFactory = new RegionFactory<>();
        SphericalPolygonsSet result = null;
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
            if (result == null) {
                result = build(polygon);
                continue;
            }
            SphericalPolygonsSet s = build(polygon);
            result = (SphericalPolygonsSet) regionFactory.union(result, s);
        }
        return result;
    }

    public static S2Point buildS2Point(Coordinate coordinate) {
        return new S2Point(FastMath.toRadians(coordinate.getX()), 0.5 * FastMath.PI - FastMath.toRadians(coordinate.getY()));
    }
}
