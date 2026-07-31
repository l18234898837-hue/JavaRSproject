package com.htcloud.orbit.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class ShpFileGenerator {
    public static void main(String[] args) {
        try {
            String geoJsonStr = "{\"features\":[{\"geometry\":{\"coordinates\":[[[118.17676248360395,29.406853890301747],[118.17647541204545,29.40564001119853],[118.14914321175723,29.289856690630057],[118.147622305,29.288813188],[118.132129171,29.286393453],[118.116714606,29.290548574],[118.110559634,29.296155484],[118.099749461,29.2873881],[118.072845502,29.293104356],[118.066587074,29.290867904],[118.075126909,29.268858817],[118.07027917,29.249902456],[118.07586101,29.245335681],[118.076562066,29.235741832],[118.067190501,29.218931904],[118.052643367,29.21739949],[118.046173011,29.220209143],[118.03718783,29.212950829],[118.023154612,29.171938032],[118.038731841,29.160109474],[118.038135128,29.141125719],[118.033244535,29.129853523],[118.047841749,29.11894599],[118.047173244,29.115290115],[118.037639485,29.112569433],[118.032799265,29.100038152],[118.040657107,29.091590774],[118.070499118,29.077258564],[118.071609266,29.065545929],[118.06170718,29.056650844],[118.061328645,29.051445685],[118.065963052,29.039067245],[118.074768537,29.039620967],[118.082746836,29.032132605],[118.08487675391287,29.016873266178184],[118.05828911042865,28.90363776398437],[118.02887025026803,28.778122449310963],[117.99950165249794,28.652601052845423],[117.97018234847494,28.5270710830312],[117.94091314306549,28.401537627975472],[117.91169307497913,28.27599819619994],[117.88252118591815,28.150450296227152],[117.8533982837925,28.02489901562627],[117.8317749947206,27.93152082609935],[117.831340816,27.931120003],[117.808764901,27.923026002],[117.8010181,27.909791201],[117.781921489,27.899108481],[117.783293054,27.886186839],[117.777423567,27.869305277],[117.782375725,27.857613561],[117.772705383,27.841702094],[117.757243114,27.833481567],[117.749253332,27.815319099],[117.73545173,27.80375741],[117.728420205,27.804957642],[117.711745619,27.817510084],[117.699242264,27.837195233],[117.689116465,27.829457836],[117.68234533,27.831007616],[117.675255035,27.8273368],[117.668154146,27.831807624],[117.660593304,27.846701175],[117.621435564,27.866349869],[117.61142114889654,27.866347245566644],[117.6294667461969,27.944407137400056],[117.65855836083072,28.070036635952714],[117.68769816056712,28.195660794419595],[117.71688710132838,28.32128211154526],[117.74612437609096,28.44689550077565],[117.77541094507858,28.572503459029498],[117.80474777048761,28.698108478802887],[117.83413404942561,28.823705477539054],[117.86357075197067,28.949296949983214],[117.89305825814627,29.074882862539916],[117.9225975491509,29.200465711300076],[117.9521878235316,29.326040411359692],[117.98183006675698,29.451609457222677],[118.01137400884627,29.57653574493213],[118.020971283,29.57608479],[118.035317237,29.565820636],[118.049773339,29.542930056],[118.08457234,29.537325681],[118.097010436,29.540361169],[118.102630971,29.536530568],[118.100907286,29.530309953],[118.105126238,29.525127507],[118.127413222,29.513052525],[118.139147482,29.492276353],[118.119466192,29.474297807],[118.120378106,29.468731863],[118.129657573,29.467265574],[118.132804174,29.446910142],[118.122823235,29.440639315],[118.121470397,29.432691393],[118.144268224,29.415222005],[118.151743908,29.415832156],[118.174100811,29.406358157],[118.17676248360395,29.406853890301747]]],\"type\":\"Polygon\"},\"type\":\"Feature\",\"properties\":{\"countryEn\":\"China\",\"area\":\"3504.7445\",\"country\":\"中国\",\"sdate\":\"2025-10-25 06:27:04\",\"code\":\"CFOSAT\",\"num\":2,\"noradNumber\":\"43662\",\"orbitalAltitude\":600000,\"sort\":4,\"type\":\"国产公益卫星\",\"edate\":\"2025-10-25 06:29:18\",\"countryCod\":\"CHN\",\"id\":\"148\",\"line3\":\"2 43662  97.3644 236.8443 0001564  80.0276 280.1134 15.16320121376338\",\"dataSource\":\"中法海洋卫星\",\"line2\":\"1 43662U 18083A   25228.36520001  .00003558  00000-0  18692-3 0  9997\",\"sideSwingAngle\":0,\"line1\":\"CFOSAT\",\"bjsdate\":\"2025-10-25 06:27:04\",\"bjedate\":\"2025-10-25 06:29:18\"}}],\"type\":\"FeatureCollection\"}";
            String result = generateShpZip(geoJsonStr, "F:\\Download\\test_output");
            System.out.println("生成的文件路径: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 生成SHP文件并打包为ZIP
     */
    public static String generateShpZip(String geoJsonStr, String outputZipPath) throws Exception {
        log.debug("开始处理GeoJSON数据...");
        // 解析GeoJSON
        GeoJsonData geoJsonData = objectMapper.readValue(geoJsonStr, GeoJsonData.class);
        log.debug("成功解析GeoJSON，包含 {} 个要素", geoJsonData.getFeatures().size());
        // 创建临时目录
        String tempDir = Files.createTempDirectory("shp_export_").toString();
        log.debug("创建临时目录: {}", tempDir);
        String shpBaseName = "satellite_data";
        String shpPath = tempDir + File.separator + shpBaseName + ".shp";
        // 生成SHP文件
        generateShapefile(geoJsonData, shpPath);
        log.debug("SHP文件生成完成");
        // 打包为ZIP
        String zipFilePath = packShpFilesToZip(tempDir, shpBaseName, outputZipPath);
        log.info("ZIP文件打包完成: {}", zipFilePath);
        // 清理临时文件
        cleanupTempFiles(tempDir);
        log.info("临时文件清理完成");
        return zipFilePath;
    }

    /**
     * 生成SHP文件
     */
    private static void generateShapefile(GeoJsonData geoJsonData, String shpFilePath) throws Exception {
        log.debug("开始生成Shapefile: {}", shpFilePath);
        // 确定几何类型
        Class<? extends Geometry> geometryClass = determineGeometryClass(geoJsonData);
        log.debug("确定的几何类型: {}", geometryClass.getSimpleName());
        // 动态创建FeatureType
        SimpleFeatureType featureType = createDynamicFeatureType(geoJsonData, geometryClass);
        log.info("创建动态FeatureType完成");
        // 创建Shapefile数据存储
        File shpFile = new File(shpFilePath);
        ShapefileDataStore dataStore = createDataStore(shpFile, featureType);
        log.info("创建Shapefile数据存储完成");
        // 创建Feature集合
        DefaultFeatureCollection featureCollection = createDynamicFeatureCollection(geoJsonData, featureType, geometryClass);
        log.debug("创建Feature集合完成，包含 {} 个要素", featureCollection.size());
        // 写入数据
        writeFeaturesToStore(dataStore, featureCollection, featureType);
        log.info("数据写入完成");
        // 创建CPG文件指定编码
        createCpgFile(shpFilePath, "GBK");
        log.info("创建CPG编码文件完成");
        dataStore.dispose();
    }

    /**
     * 创建CPG文件指定编码
     */
    private static void createCpgFile(String shpFilePath, String encoding) throws IOException {
        String cpgFilePath = shpFilePath.replace(".shp", ".cpg");
        try (FileWriter writer = new FileWriter(cpgFilePath)) {
            writer.write(encoding);
        }
    }

    /**
     * 确定几何类型
     */
    private static Class<? extends Geometry> determineGeometryClass(GeoJsonData geoJsonData) {
        if (geoJsonData.getFeatures() == null || geoJsonData.getFeatures().isEmpty()) {
            return Polygon.class;
        }

        Map<String, Integer> typeCount = new HashMap<>();
        for (GeoJsonData.Feature feature : geoJsonData.getFeatures()) {
            if (feature.getGeometry() != null && feature.getGeometry().getType() != null) {
                String type = feature.getGeometry().getType();
                typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
            }
        }

        return typeCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> mapGeometryClass(entry.getKey()))
            .orElse(null);
    }

    /**
     * 映射几何类型到具体的JTS类
     */
    private static Class<? extends Geometry> mapGeometryClass(String geoJsonType) {
        if (geoJsonType == null) return Polygon.class;

        switch (geoJsonType.toUpperCase()) {
            case "POINT": return Point.class;
            case "LINESTRING": return LineString.class;
            case "MULTIPOINT": return MultiPoint.class;
            case "MULTILINESTRING": return MultiLineString.class;
            case "MULTIPOLYGON": return MultiPolygon.class;
            case "POLYGON":
            default: return Polygon.class;
        }
    }

    /**
     * 动态创建FeatureType - 根据实际属性动态生成字段
     */
    private static SimpleFeatureType createDynamicFeatureType(GeoJsonData geoJsonData, Class<? extends Geometry> geometryClass) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("DynamicData");
        builder.setCRS(DefaultGeographicCRS.WGS84);
        // 添加几何字段
        builder.add("the_geom", geometryClass);
        // 收集所有属性字段
        Set<String> fieldNames = collectAllFieldNames(geoJsonData);
        // 为每个属性字段添加对应的列
        for (String fieldName : fieldNames) {
            String validFieldName = getValidFieldName(fieldName);
            // 使用String类型存储所有属性，避免类型转换问题
            builder.add(validFieldName, String.class);
        }
        return builder.buildFeatureType();
    }

    /**
     * 收集所有属性字段名
     */
    private static Set<String> collectAllFieldNames(GeoJsonData geoJsonData) {
        Set<String> fieldNames = new LinkedHashSet<>(); // 保持顺序
        for (GeoJsonData.Feature feature : geoJsonData.getFeatures()) {
            if (feature.getProperties() != null) {
                fieldNames.addAll(feature.getProperties().keySet());
            }
        }
        log.debug("收集到的字段名: {}", fieldNames);
        return fieldNames;
    }

    /**
     * 获取有效的字段名（不超过10个字符）
     */
    private static String getValidFieldName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "field";
        }
        // 移除特殊字符，只保留字母、数字和下划线
        String cleaned = originalName.replaceAll("[^a-zA-Z0-9_]", "_");
        // 如果长度超过10个字符，进行截断
        if (cleaned.length() > 10) {
            return cleaned.substring(0, 10);
        }
        return cleaned;
    }

    /**
     * 创建Shapefile数据存储 - 使用GBK编码解决中文乱码
     */
    private static ShapefileDataStore createDataStore(File shpFile, SimpleFeatureType featureType) throws Exception {
        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        Map<String, Serializable> params = new HashMap<>();
        params.put("url", shpFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);
        ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
        dataStore.createSchema(featureType);
        // 使用GBK编码解决中文乱码
        dataStore.setCharset(Charset.forName("GBK"));
        return dataStore;
    }

    /**
     * 动态创建Feature集合
     */
    private static DefaultFeatureCollection createDynamicFeatureCollection(GeoJsonData geoJsonData,
                                                                           SimpleFeatureType featureType,
                                                                           Class<? extends Geometry> geometryClass) {
        DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);

        for (GeoJsonData.Feature feature : geoJsonData.getFeatures()) {
            try {
                // 设置几何图形
                Geometry geometry = convertToJTSGeometry(feature.getGeometry());
                if (geometry == null) {
                    log.error("几何图形为空，跳过该要素");
                    continue;
                }
                // 确保几何类型与schema匹配
                if (!geometryClass.isInstance(geometry)) {
                    geometry = convertToTargetType(geometry, geometryClass);
                }
                featureBuilder.set("the_geom", geometry);
                // 动态设置属性 - 前端传什么就写什么
                Map<String, Object> properties = feature.getProperties();
                if (properties != null) {
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        String fieldName = getValidFieldName(entry.getKey());
                        Object value = entry.getValue();
                        // 将值转换为字符串（DBF文件主要支持字符串）
                        String stringValue = value != null ? value.toString() : "";
                        featureBuilder.set(fieldName, stringValue);
                    }
                }
                SimpleFeature simpleFeature = featureBuilder.buildFeature(null);
                featureCollection.add(simpleFeature);

            } catch (Exception e) {
                log.error("处理要素时出错: {}", e.getMessage());
                e.printStackTrace();
            }
        }
        return featureCollection;
    }

    /**
     * 将几何图形转换为目标类型
     */
    private static Geometry convertToTargetType(Geometry geometry, Class<? extends Geometry> targetClass) {
        if (geometry == null) return null;

        if (targetClass.equals(Polygon.class) && geometry instanceof MultiPolygon) {
            MultiPolygon multi = (MultiPolygon) geometry;
            return multi.getNumGeometries() > 0 ? (Polygon) multi.getGeometryN(0) : geometry;
        } else if (targetClass.equals(MultiPolygon.class) && geometry instanceof Polygon) {
            return geometryFactory.createMultiPolygon(new Polygon[]{(Polygon) geometry});
        } else if (targetClass.equals(Point.class) && geometry instanceof MultiPoint) {
            MultiPoint multi = (MultiPoint) geometry;
            return multi.getNumGeometries() > 0 ? (Point) multi.getGeometryN(0) : geometry;
        } else if (targetClass.equals(MultiPoint.class) && geometry instanceof Point) {
            return geometryFactory.createMultiPoint(new Point[]{(Point) geometry});
        } else if (targetClass.equals(LineString.class) && geometry instanceof MultiLineString) {
            MultiLineString multi = (MultiLineString) geometry;
            return multi.getNumGeometries() > 0 ? (LineString) multi.getGeometryN(0) : geometry;
        } else if (targetClass.equals(MultiLineString.class) && geometry instanceof LineString) {
            return geometryFactory.createMultiLineString(new LineString[]{(LineString) geometry});
        }
        return geometry;
    }

    /**
     * 将GeoJSON几何图形转换为JTS几何图形
     */
    private static Geometry convertToJTSGeometry(GeoJsonData.Geometry geoJsonGeometry) {
        if (geoJsonGeometry == null) {
            return null;
        }
        String geometryType = geoJsonGeometry.getType();
        if (geometryType == null) {
            return null;
        }
        try {
            switch (geometryType.toUpperCase()) {
                case "POLYGON":
                    return createPolygon(geoJsonGeometry.getCoordinates());
                case "MULTIPOLYGON":
                    return createMultiPolygon(geoJsonGeometry.getCoordinates());
                case "POINT":
                    return createPoint(geoJsonGeometry.getCoordinates());
                case "LINESTRING":
                    return createLineString(geoJsonGeometry.getCoordinates());
                case "MULTIPOINT":
                    return createMultiPoint(geoJsonGeometry.getCoordinates());
                case "MULTILINESTRING":
                    return createMultiLineString(geoJsonGeometry.getCoordinates());
                case "GEOMETRYCOLLECTION":
                    return createGeometryCollection(geoJsonGeometry.getGeometries());
                default:
                    System.err.println("不支持的几何类型: " + geometryType);
                    return null;
            }
        } catch (Exception e) {
            System.err.println("转换几何类型时出错: " + geometryType + ", 错误: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Polygon createPolygon(Object coordinates) {
        try {
            List<List<List<Double>>> coordList = (List<List<List<Double>>>) coordinates;
            if (coordList == null || coordList.isEmpty()) {
                return null;
            }
            LinearRing shell = null;
            List<LinearRing> holes = new ArrayList<>();

            for (int i = 0; i < coordList.size(); i++) {
                List<List<Double>> ring = coordList.get(i);
                Coordinate[] coords = convertCoordinates(ring);
                if (i == 0) {
                    shell = geometryFactory.createLinearRing(coords);
                } else {
                    holes.add(geometryFactory.createLinearRing(coords));
                }
            }
            return shell != null ? geometryFactory.createPolygon(shell, holes.toArray(new LinearRing[0])) : null;
        } catch (Exception e) {
            log.error("创建Polygon时出错: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static MultiPolygon createMultiPolygon(Object coordinates) {
        try {
            List<List<List<List<Double>>>> coordList = (List<List<List<List<Double>>>>) coordinates;
            if (coordList == null || coordList.isEmpty()) {
                return null;
            }
            Polygon[] polygons = new Polygon[coordList.size()];
            for (int i = 0; i < coordList.size(); i++) {
                List<List<List<Double>>> polygonCoords = coordList.get(i);
                polygons[i] = createPolygon(polygonCoords);
            }
            return geometryFactory.createMultiPolygon(polygons);
        } catch (Exception e) {
            log.debug("创建MultiPolygon时出错: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Point createPoint(Object coordinates) {
        try {
            List<Double> coordList = (List<Double>) coordinates;
            if (coordList == null || coordList.size() < 2) {
                return null;
            }
            Coordinate coord = new Coordinate(coordList.get(0), coordList.get(1));
            return geometryFactory.createPoint(coord);
        } catch (Exception e) {
            log.debug("创建Point时出错: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static LineString createLineString(Object coordinates) {
        try {
            List<List<Double>> coordList = (List<List<Double>>) coordinates;
            if (coordList == null || coordList.isEmpty()) {
                return null;
            }
            Coordinate[] coords = convertCoordinates(coordList);
            return geometryFactory.createLineString(coords);
        } catch (Exception e) {
            log.debug("创建LineString时出错: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static MultiPoint createMultiPoint(Object coordinates) {
        try {
            List<List<Double>> coordList = (List<List<Double>>) coordinates;
            if (coordList == null || coordList.isEmpty()) {
                return null;
            }
            Point[] points = new Point[coordList.size()];
            for (int i = 0; i < coordList.size(); i++) {
                points[i] = createPoint(coordList.get(i));
            }
            return geometryFactory.createMultiPoint(points);
        } catch (Exception e) {
            log.debug("创建MultiPoint时出错: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static MultiLineString createMultiLineString(Object coordinates) {
        try {
            List<List<List<Double>>> coordList = (List<List<List<Double>>>) coordinates;
            if (coordList == null || coordList.isEmpty()) {
                return null;
            }
            LineString[] lineStrings = new LineString[coordList.size()];
            for (int i = 0; i < coordList.size(); i++) {
                lineStrings[i] = createLineString(coordList.get(i));
            }
            return geometryFactory.createMultiLineString(lineStrings);
        } catch (Exception e) {
            log.debug("创建MultiLineString时出错: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static GeometryCollection createGeometryCollection(List<GeoJsonData.Geometry> geometries) {
        try {
            if (geometries == null || geometries.isEmpty()) {
                return null;
            }
            Geometry[] geomArray = new Geometry[geometries.size()];
            for (int i = 0; i < geometries.size(); i++) {
                geomArray[i] = convertToJTSGeometry(geometries.get(i));
            }
            return geometryFactory.createGeometryCollection(geomArray);
        } catch (Exception e) {
            log.debug("创建GeometryCollection时出错: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static Coordinate[] convertCoordinates(List<List<Double>> coordList) {
        if (coordList == null) {
            return new Coordinate[0];
        }
        Coordinate[] coordinates = new Coordinate[coordList.size()];
        for (int i = 0; i < coordList.size(); i++) {
            List<Double> coord = coordList.get(i);
            if (coord != null && coord.size() >= 2) {
                coordinates[i] = new Coordinate(coord.get(0), coord.get(1));
            } else {
                coordinates[i] = new Coordinate(0, 0);
            }
        }
        return coordinates;
    }

    private static void writeFeaturesToStore(ShapefileDataStore dataStore,
                                             DefaultFeatureCollection featureCollection,
                                             SimpleFeatureType featureType) throws Exception {
        if (featureCollection.isEmpty()) {
            throw new RuntimeException("没有要素可以写入");
        }
        Transaction transaction = new DefaultTransaction("create");
        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);

        if (featureSource instanceof SimpleFeatureStore) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
            featureStore.setTransaction(transaction);

            try {
                featureStore.addFeatures(featureCollection);
                transaction.commit();
                log.debug("成功写入 {} 个要素到Shapefile", featureCollection.size());
            } catch (Exception e) {
                transaction.rollback();
                throw new RuntimeException("写入要素失败: " + e.getMessage(), e);
            } finally {
                transaction.close();
            }
        } else {
            throw new RuntimeException("Feature store is not writable");
        }
    }

    private static String packShpFilesToZip(String tempDir, String shpBaseName, String outputZipPath) throws Exception {
        // 确保输出目录存在
        File outputDir = new File(outputZipPath).getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs();
        }
        String zipFilePath = outputZipPath.endsWith(".zip") ? outputZipPath : outputZipPath + ".zip";
        log.debug("打包文件到: {}", zipFilePath);
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            String[] extensions = {".shp", ".shx", ".dbf", ".prj", ".cpg", ".fix"};
            for (String ext : extensions) {
                String fileName = shpBaseName + ext;
                Path filePath = Paths.get(tempDir, fileName);
                if (Files.exists(filePath)) {
                    ZipEntry zipEntry = new ZipEntry(fileName);
                    zos.putNextEntry(zipEntry);
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                    log.debug("已添加文件: {}", fileName);
                } else {
                    log.error("文件不存在: {}", filePath);
                }
            }
        }
        return zipFilePath;
    }

    private static void cleanupTempFiles(String tempDir) throws Exception {
        File tempDirFile = new File(tempDir);
        if (tempDirFile.exists()) {
            File[] files = tempDirFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        log.error("无法删除文件: {}", file.getAbsolutePath());
                    }
                }
            }
            if (!tempDirFile.delete()) {
                log.error("无法删除临时目录: {}", tempDir);
            }
        }
    }
}
