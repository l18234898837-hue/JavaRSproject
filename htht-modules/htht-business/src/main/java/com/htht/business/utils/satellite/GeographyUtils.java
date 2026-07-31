package com.htht.business.utils.satellite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.business.utils.satellite.domain.Region;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;

import java.io.IOException;
import java.util.List;

/**
 * 处理 PostgreSQL geography 类型的工具类
 */
public class GeographyUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
//    private static final GeoJsonReader geoJsonReader = new GeoJsonReader(geometryFactory);
//    private static final GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
    private static final WKTReader wktReader = new WKTReader(geometryFactory);

    /**
     * 从 WKT (Well-Known Text) 字符串创建 Geometry 对象
     * @param wkt WKT 字符串
     * @return Geometry 对象
     */
//    public static Geometry wktToGeometry(String wkt) {
//        return wktReader.read(wkt);
//    }

    /**
     * 从 GeoJSON 字符串创建 Geometry 对象
     * @param geoJson GeoJSON 字符串
     * @return Geometry 对象
     */
//    public static Geometry geoJsonToGeometry(String geoJson) {
//       return geoJsonReader.read(geoJson);
//    }

    /**
     * 将 Geometry 对象转换为 GeoJSON 字符串
     * @param geometry Geometry 对象
     * @return GeoJSON 字符串
     */
//    public static String geometryToGeoJson(Geometry geometry) {
//        return geoJsonWriter.write(geometry);
//    }

    /**
     * 将 Geometry 对象转换为 WKT 字符串
     * @param geometry Geometry 对象
     * @return WKT 字符串
     */
    public static String geometryToWkt(Geometry geometry) {
        return geometry.toString();
    }


    /**
     * 计算几何对象的中心点
     * @param geometry 几何对象
     * @return 中心点坐标 [经度, 纬度]
     */
    public static double[] calculateCentroid(Geometry geometry) {
        if (geometry == null) {
            return new double[]{0, 0};
        }
        return new double[]{
            geometry.getCentroid().getX(),
            geometry.getCentroid().getY()
        };
    }

    /**
     * 计算几何对象的面积（平方米）
     * @param geometry 几何对象
     * @return 面积（平方米）
     */
    public static double calculateArea(Geometry geometry) {
        if (geometry == null) {
            return 0;
        }
        return geometry.getArea();
    }

    /**
     * 计算两个几何对象之间的距离（米）
     * @param geom1 第一个几何对象
     * @param geom2 第二个几何对象
     * @return 距离（米）
     */
    public static double calculateDistance(Geometry geom1, Geometry geom2) {
        if (geom1 == null || geom2 == null) {
            return 0;
        }
        return geom1.distance(geom2);
    }

    /**
     * 检查一个点是否在几何对象内
     * @param point 点几何对象
     * @param geometry 几何对象
     * @return 是否在内部
     */
    public static boolean isPointInGeometry(Geometry point, Geometry geometry) {
        if (point == null || geometry == null) {
            return false;
        }
        return geometry.contains(point);
    }

    /**
     * 创建点几何对象
     * @param lon 经度
     * @param lat 纬度
     * @return 点几何对象
     */
    public static Geometry createPoint(double lon, double lat) {
        return geometryFactory.createPoint(new org.locationtech.jts.geom.Coordinate(lon, lat));
    }


}
