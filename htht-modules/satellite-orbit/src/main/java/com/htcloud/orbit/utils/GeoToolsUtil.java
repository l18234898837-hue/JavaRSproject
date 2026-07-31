package com.htcloud.orbit.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.shapefile.ShapefileDumper;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * geotools工具类
 *
 * @author gersy
 * @date 2021-05-10
 */
public class GeoToolsUtil {


    private static Logger logger = LoggerFactory.getLogger(GeoToolsUtil.class);

    private static PrecisionModel precisionModel = new PrecisionModel(1);
    private static GeometryFactory geometryFactory = new GeometryFactory(precisionModel, 0);

    /**
     * shp转geojson文件
     */
    public static void shp2GeoJsonFile(File shpFile, File geoJsonFile, Charset charset) throws IOException {
        String geoJson = shp2GeoJson(shpFile, charset);
        FileUtils.writeStringToFile(geoJsonFile, geoJson, "utf-8");
    }

    /**
     * shp转geojson
     */
    public static String shp2GeoJson(File file, Charset charset) {
        DataStore dataStore = buildDataStore(file, charset);
        try {
            StringWriter writer = new StringWriter();
            String[] typeNames = dataStore.getTypeNames();
            if (typeNames.length > 1) {
                writer.write("[");
            }
            FeatureJSON json = new FeatureJSON();
            for (int i = 0; i < typeNames.length; i++) {
                if (i > 0) {
                    writer.write(",");
                }
                String typeName = typeNames[i];
                writer.write("{\n" +
                        "\"type\": \"FeatureCollection\",\n" +
                        "\"name\": \"" + typeName + "\",\n" +
                        "\"features\": [");
                FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
                FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures();
                FeatureIterator<SimpleFeature> features = collection.features();
                int index = 0;
                while (features.hasNext()) {
                    if (index > 0) {
                        writer.write(",");
                    }
                    SimpleFeature feature = features.next();
                    json.writeFeature(feature, writer);
                    index++;
                }
                writer.write("]}");
            }
            if (typeNames.length > 1) {
                writer.write("]");
            }
            return writer.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
        }
        return null;
    }

    public static List<Geometry> convertShapefileToGeometry(String shapefilePath) {
        List<Geometry> geometries = new ArrayList<>();
        try {
            File file = new File(shapefilePath);
            Map<String, Object> map = new HashMap<>();
            map.put("url", file.toURI().toURL());
            DataStore dataStore = DataStoreFinder.getDataStore(map);
            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
            SimpleFeatureCollection collection = featureSource.getFeatures();
            try (SimpleFeatureIterator iterator = collection.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    // 获取Geometry对象
                    Geometry geometry = (Geometry) feature.getDefaultGeometry();
                    geometries.add(geometry);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return geometries;
    }

    private static boolean isValid(Geometry geometry) {
        IsValidOp isValidOp = new IsValidOp(geometry);
        TopologyValidationError validationError = isValidOp.getValidationError();
        if (validationError == null) {
            return true;
        } else {
            String msg = validationError.getMessage();
            logger.error(msg);
            return false;
        }
    }


    /**
     * wkt转geojson
     */
    public static String wkt2json(String wkt) {
        String json = null;
        try {
            WKTReader reader = new WKTReader();
            Geometry geometry = reader.read(wkt);
            StringWriter writer = new StringWriter();
            GeometryJSON g = new GeometryJSON(10);
            g.write(geometry, writer);
            json = writer.toString();
        } catch (Exception e) {
            logger.error("{0}", e);
        }
        return json;
    }

    public static String geometry2Json(Geometry geometry) {
        GeometryJSON g = new GeometryJSON(10);
        StringWriter writer = new StringWriter();
        try {
            g.write(geometry, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    public static Geometry json2Geometry(String geojson) {
        GeometryJSON json = new GeometryJSON(20);
        Reader reader = new StringReader(geojson);
        try {
            return json.read(reader);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static double getArea(Geometry geometry) throws FactoryException {
        return transform(geometry, "EPSG:32649").getArea() / 1E6;
    }

    public static Geometry transform(Geometry geometry, String targetCRS) throws FactoryException {
        CoordinateReferenceSystem crs = CRS.decode(targetCRS);
        CoordinateReferenceSystem sourceCRS = JTS.getCRS(geometry);
        if (sourceCRS == null) {
            sourceCRS = DefaultGeographicCRS.WGS84;
        }
        return transform(geometry, sourceCRS, crs);
    }

    public static Geometry transform(Geometry geometry, CoordinateReferenceSystem sourceCRS, CoordinateReferenceSystem targetCRS) {
        try {
            if (targetCRS.equals(sourceCRS)) {
                return geometry;
            }
            MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
            return JTS.transform(geometry, transform);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * geojson转wkt
     */
    public static String json2wkt(String json) {
        GeometryJSON gjson = new GeometryJSON(20);
        Reader reader = new StringReader(json);
        String ret = null;
        try {
            Geometry geometry = gjson.read(reader);
            ret = geometry.toText();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    /**
     * 读取wkt
     */
    public static Geometry readWkt(String wkt) {
        WKTReader reader = new WKTReader();
        Geometry geometry = null;
        try {
            geometry = reader.read(wkt);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return geometry;
    }

    /**
     * 经纬度相反问题处理
     */
    public static String lonLatHandle(String wkt) {
        Geometry geometry = readWkt(wkt);
        Coordinate[] coordinates = geometry.getCoordinates();
        for (Coordinate coordinate : coordinates) {
            double x = coordinate.getX();
            double y = coordinate.getY();
            // 位置相反 手动给调回来
            if (y > x) {
                coordinate.setX(y);
                coordinate.setY(x);
            }
        }
        return geometry.toText();
    }


    /**
     * 获取面积最大的凸多边形
     */
    public static Geometry getMaxPolygonByPoints(List<Coordinate> coordinates) {
        Coordinate[] coordinateArray = CoordinateArrays.toCoordinateArray(coordinates);
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        // 使用凸包算法获取面积最大的凸多边形
        ConvexHull convexHull = new ConvexHull(coordinateArray, geometryFactory);
        return convexHull.getConvexHull();
    }

    /**
     * 将排好顺序的点连成面
     */
    public static String pointsToWkt(List<Coordinate> coordinates) {
        return pointsToPolygon(coordinates).toText();
    }

    /**
     * 将排好顺序的点连成面
     */
    public static Polygon pointsToPolygon(List<Coordinate> coordinates) {
        GeometryFactory factory = JTSFactoryFinder.getGeometryFactory();
        // 定义点的坐标数组
        Coordinate[] coordinateArray = CoordinateArrays.toCoordinateArray(coordinates);
        // 创建线性环（LinearRing）
        LinearRing linearRing = factory.createLinearRing(coordinateArray);
        // 创建多边形（Polygon）
        return factory.createPolygon(linearRing, null);
    }


    public static ShapefileDataStore buildDataStore(File shpFile, Charset charset) {
        ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
        try {
            ShapefileDataStore dataStore = (ShapefileDataStore) factory.createDataStore(shpFile.toURI().toURL());
            if (dataStore != null) {
                dataStore.setCharset(charset == null ? StandardCharsets.UTF_8 : charset);
            }
            return dataStore;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 去掉重复的点
     */
    private static Coordinate[] duplicateRemove(Coordinate[] coordinates) {
        Stack<Coordinate> coordinateStack = new Stack<>();
        Coordinate lastCord = null;
        Coordinate nowCord = null;

        for (int i = 0; i < coordinates.length; i++) {
            nowCord = coordinates[i];
            //相邻点一样，则剔除
            if (lastCord != null && nowCord.equals(lastCord)) {
                continue;
            }
            lastCord = nowCord;
            coordinateStack.push(nowCord);
        }
        List<Coordinate> coordinateList = new ArrayList<>(coordinateStack);
        return coordinateList.toArray(new Coordinate[]{});
    }

    /**
     * 利用jts的两线相交方法解决线的自相交问题
     */
    private static Geometry getPolygonByInteriorPoint(Polygon polygon) {
        IsValidOp isValidOp = new IsValidOp(polygon);
        TopologyValidationError validationError = isValidOp.getValidationError();

        LineString exteriorRing = polygon.getExteriorRing();
        Coordinate[] coordinates = exteriorRing.getCoordinates();
        Coordinate[] duplicateCoords = duplicateRemove(coordinates);
        Geometry exteriorPolygon = polygon.getFactory().createPolygon(duplicateCoords);
        int numInteriorRing = polygon.getNumInteriorRing();
        //对内部进行挖空
        for (int i = 0; i < numInteriorRing; i++) {
            LineString interiorRingN = polygon.getInteriorRingN(i);
            Coordinate[] coords = duplicateRemove(interiorRingN.getCoordinates());
            Polygon interiorPolygon = polygon.getFactory().createPolygon(coords);
            exteriorPolygon = exteriorPolygon.difference(interiorPolygon);
        }
        return exteriorPolygon.intersection(exteriorPolygon);
    }

    private static boolean intersects(Point point, List<Geometry> geometries) {
        for (Geometry geometry : geometries) {
            boolean intersects = geometry.intersects(point);
            if (intersects) {
                return true;
            }
        }
        return false;
    }

    public static GeometryFactory getGeometryFactory() {
        return JTSFactoryFinder.getGeometryFactory();
    }

    // 轨迹延长
    public static LineString extendLineStringWithVector(LineString lineString, double distance) {
        GeometryFactory geometryFactory = lineString.getFactory();
        Coordinate[] coords = lineString.getCoordinates();
        Coordinate start = coords[0];
        Coordinate end = coords[coords.length - 1];

        // 计算向量
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);

        // 计算新的起点和终点
        Coordinate newStart = new Coordinate(start.x - (dx * distance / length), start.y - (dy * distance / length));
        Coordinate newEnd = new Coordinate(end.x + (dx * distance / length), end.y + (dy * distance / length));

        // 创建新的坐标数组
        Coordinate[] newCoords = new Coordinate[]{newStart, newEnd};
        return geometryFactory.createLineString(newCoords);
    }

    public static CoordinateSequenceFactory getCoordinateSequenceFactory() {
        return getGeometryFactory().getCoordinateSequenceFactory();
    }

    public static CoordinateSequence createCoordinateSequence2D(int size) {
        return getCoordinateSequenceFactory().create(size, 2);
    }

    public static double calculateLineStringLengthInMeters(LineString lineString) {
        Coordinate[] coords = lineString.getCoordinates();
        Coordinate p1 = coords[0];
        Coordinate p2 = coords[coords.length - 1];
        double lat1 = Math.toRadians(p1.y);
        double lon1 = Math.toRadians(p1.x);
        double lat2 = Math.toRadians(p2.y);
        double lon2 = Math.toRadians(p2.x);
        double vLon = Math.abs(lon1 - lon2);
        double vLat = Math.abs(lat1 - lat2);
        double h = haverSin(vLat) + Math.cos(lat1) * Math.cos(lat2) * haverSin(vLon);
        double distance = 1.2756274E7 * Math.asin(Math.sqrt(h));
        return distance;
    }

    private static double haverSin(double theta) {
        double v = Math.sin(theta / 2.0);
        return v * v;
    }


    public static double meterToWgs84(double meter, boolean isLongitude) {
        try {
            CoordinateReferenceSystem webMercatorCRS = CRS.decode("EPSG:3857");
            MathTransform mathTransform = CRS.findMathTransform(webMercatorCRS, DefaultGeographicCRS.WGS84, true);
            double[] src = new double[]{0, 0};
            int index = isLongitude ? 0 : 1;
            src[index] = meter;
            double[] dest = new double[2];
            mathTransform.transform(src, 0, dest, 0, 1);
            return dest[index];
        } catch (FactoryException | TransformException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从线与面的交集中提取“最高”和“最低”纬度点，构造一条垂直线段
     *
     * @param intersection 线与面的交集几何对象
     * @return 构造出的垂直线段（LineString），或 null（无法构造）
     */
    public static LineString createVerticalLineFromIntersection(Geometry intersection) {
        if (intersection == null || intersection.isEmpty()) {
            return null;
        }

        List<Coordinate> coordinates = new ArrayList<>();

        // 根据交集类型提取坐标点
        if (intersection instanceof LineString) {
            coordinates.addAll(Arrays.asList(intersection.getCoordinates()));
        } else if (intersection instanceof MultiLineString) {
            for (int i = 0; i < intersection.getNumGeometries(); i++) {
                LineString line = (LineString) intersection.getGeometryN(i);
                coordinates.addAll(Arrays.asList(line.getCoordinates()));
            }
        } else if (intersection instanceof Point) {
            coordinates.add(intersection.getCoordinate());
        } else if (intersection instanceof MultiPoint) {
            for (int i = 0; i < intersection.getNumGeometries(); i++) {
                coordinates.add(intersection.getGeometryN(i).getCoordinate());
            }
        } else {
            return null; // 不支持其他类型
        }

        if (coordinates.size() < 2) {
            return null; // 至少需要两个点
        }

        // 按纬度排序，取最底点和顶点
        coordinates.sort(Comparator.comparingDouble(c -> c.y));
        Coordinate bottom = coordinates.get(0);
        Coordinate top = coordinates.get(coordinates.size() - 1);

        GeometryFactory geometryFactory = GeoToolsUtil.getGeometryFactory();
        return geometryFactory.createLineString(new Coordinate[]{bottom, top});
    }

    /**
     * 从线与面的交集中提取“最早”和“最晚”的交点，构造一条线段，保持原线方向
     *
     * @param originalLine 原始线对象
     * @param intersection 线与面的交集几何对象
     * @return 构造出的线段（保持原线方向），或 null（无法构造）
     */
    public static LineString createLineFromIntersectionAlongOriginalDirection(LineString originalLine, Geometry intersection) {
        if (intersection == null || intersection.isEmpty()) {
            return null;
        }

        Geometry newGeometry = originalLine.intersection(intersection);
        LineString newLine = createVerticalLineFromIntersection(newGeometry);
        Coordinate[] coordinates = newLine.getCoordinates();
        if (coordinates == null || coordinates.length != 2) {
            throw new IllegalArgumentException("必须提供两个坐标点");
        }

        // 获取原始线的起点和终点
        Coordinate originalStart = originalLine.getCoordinateN(0);

        // 提取两个候选点
        Coordinate candidateA = coordinates[0];
        Coordinate candidateB = coordinates[1];

        // 计算距离
        double distToA = originalStart.distance(candidateA);
        double distToB = originalStart.distance(candidateB);

        // 判断哪个点更接近起点
        Coordinate startPoint, endPoint;

        if (distToA < distToB) {
            startPoint = candidateA;
            endPoint = candidateB;
        } else {
            startPoint = candidateB;
            endPoint = candidateA;
        }

        // 构造新的线段
        GeometryFactory geometryFactory = GeoToolsUtil.getGeometryFactory();
        return geometryFactory.createLineString(new Coordinate[]{startPoint, endPoint});
    }


    public static Geometry createPolygonFromLine(double buffer, LineString lineString) {
        // 将幅宽从米转换为 WGS84 下的经纬度单位
        double bufferDistance = GeoToolsUtil.meterToWgs84(buffer, true);

        // 设置 Buffer 参数
        BufferParameters bufferParams = new BufferParameters();
        bufferParams.setEndCapStyle(BufferParameters.CAP_FLAT); // 关键：设置为平头
        bufferParams.setQuadrantSegments(1); // 可选：减少圆弧精度以提高性能

        // 执行 buffer 操作
        return BufferOp.bufferOp(lineString, bufferDistance, bufferParams);
    }


    public static void dumpToShp(SimpleFeatureCollection featureCollection, File shpDirectory) {
        ShapefileDumper shapefileDumper = new ShapefileDumper(shpDirectory);
        shapefileDumper.setCharset(StandardCharsets.UTF_8);
        try {
            shapefileDumper.dump(featureCollection);
        } catch (IOException e) {
            throw new RuntimeException("dump failed", e);
        }
    }

    public static Geometry validGeometry(Geometry geometry) {
        if (!geometry.isValid()) {
            return geometry.buffer(0);
        }
        return geometry;
    }

    /**
     * 将坐标列表转换为 GeoJSON Polygon 对象（不格式化的紧凑JSON）
     * @param coordinates 坐标列表
     * @return GeoJSON 字符串（紧凑格式）
     */
    public static String createGeoJsonPolygonCompact(List<List<Double>> coordinates) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode geoJson = mapper.createObjectNode();

            geoJson.put("type", "Polygon");

            ArrayNode coordinatesArray = mapper.createArrayNode();
            ArrayNode ringArray = mapper.createArrayNode();

            for (List<Double> coord : coordinates) {
                if (coord.size() >= 2) {
                    ArrayNode pointArray = mapper.createArrayNode();
                    pointArray.add(coord.get(0));
                    pointArray.add(coord.get(1));
                    ringArray.add(pointArray);
                }
            }

            // 确保多边形闭合
            if (!ringArray.isEmpty()) {
                ArrayNode firstPoint = (ArrayNode) ringArray.get(0);
                ArrayNode lastPoint = (ArrayNode) ringArray.get(ringArray.size() - 1);

                if (!firstPoint.equals(lastPoint)) {
                    ringArray.add(firstPoint);
                }
            }

            coordinatesArray.add(ringArray);
            geoJson.set("coordinates", coordinatesArray);

            return mapper.writeValueAsString(geoJson);
        } catch (Exception e) {
            throw new RuntimeException("生成 GeoJSON 失败", e);
        }
    }


    /**
     * 根据坐标数组创建多边形
     * @param coordinates 坐标数组
     * @return 多边形几何对象
     */
    public static Polygon createPolygonFromCoordinates(Coordinate[] coordinates) {
        try {
            // 确保多边形是闭合的（第一个和最后一个坐标相同）
            if (coordinates.length > 0 && !coordinates[0].equals(coordinates[coordinates.length - 1])) {
                // 如果没有闭合，创建闭合的坐标数组
                Coordinate[] closedCoordinates = new Coordinate[coordinates.length + 1];
                System.arraycopy(coordinates, 0, closedCoordinates, 0, coordinates.length);
                closedCoordinates[coordinates.length] = new Coordinate(coordinates[0]);
                coordinates = closedCoordinates;
            }

            // 创建线性环
            LinearRing shell = geometryFactory.createLinearRing(coordinates);

            // 创建多边形（没有孔洞）
            return geometryFactory.createPolygon(shell);
        } catch (Exception e) {
            throw new RuntimeException("创建多边形失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据坐标列表创建多边形
     * @param coordinates 坐标列表
     * @return 多边形几何对象
     */
    public static Polygon createPolygonFromCoordinates(List<Coordinate> coordinates) {
        return createPolygonFromCoordinates(coordinates.toArray(new Coordinate[0]));
    }

    /**
     * 根据经纬度列表创建多边形
     * @param points 经纬度列表 [[lon, lat], [lon, lat], ...]
     * @return 多边形几何对象
     */
    public static Polygon createPolygonFromDoubleList(List<List<Double>> points) {
        Coordinate[] coordinates = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            List<Double> point = points.get(i);
            coordinates[i] = new Coordinate(point.get(0), point.get(1));
        }
        return createPolygonFromCoordinates(coordinates);
    }

}
