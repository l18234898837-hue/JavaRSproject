package com.htcloud.orbit.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ShpGenerator {

    public static File generateShpZipFromGeoJson(String geoJsonStr, String baseName) throws Exception {
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("shp_");
        String shpPath = tempDir.resolve(baseName + ".shp").toString();

        // 1. 解析GeoJSON字符串
        JSONObject geoJson = new JSONObject(geoJsonStr);
        JSONArray features = geoJson.getJSONArray("features");
        if (features.isEmpty()) throw new Exception("No features found in GeoJSON");

        // 2. 创建要素集合
        DefaultFeatureCollection collection = new DefaultFeatureCollection();

        // 3. 创建要素类型
        SimpleFeatureType featureType = createFeatureType();
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        for (int i = 0; i < features.size(); i++) {
            JSONObject feature = features.getJSONObject(i);
            // 4. 解析几何数据
            JSONObject geometry = feature.getJSONObject("geometry");
            JSONObject properties = feature.getJSONObject("properties");

            Polygon polygon = parsePolygon(geometry, geometryFactory);
            if (polygon == null) continue;

            // 5. 创建要素
            SimpleFeature simpleFeature = createFeature(
                featureType,
                polygon,
                properties
            );
            collection.add(simpleFeature);
        }

        // 6. 创建Shapefile
        createShapefile(shpPath, featureType, collection);

        // 7. 打包为ZIP文件
        return createZipFile(tempDir, baseName);
    }

    private static SimpleFeatureType createFeatureType() {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("SatelliteCoverage");
        builder.setCRS(org.geotools.referencing.crs.DefaultGeographicCRS.WGS84);

        // 添加几何字段
        builder.add("geometry", Polygon.class);

        // 添加属性字段
        builder.add("country", String.class);
        builder.add("area", Double.class);
        builder.add("sdate", String.class);
        builder.add("edate", String.class);
        builder.add("altitude", Integer.class);
        builder.add("satellite", String.class);
        builder.add("norad_num", String.class);
        builder.add("data_source", String.class);

        return builder.buildFeatureType();
    }

    private static Polygon parsePolygon(JSONObject geometry, GeometryFactory factory) {
        if (!"Polygon".equals(geometry.getStr("type"))) return null;

        JSONArray coordinates = geometry.getJSONArray("coordinates");
        if (coordinates.isEmpty()) return null;

        // 只处理第一个环（忽略内环）
        JSONArray ring = coordinates.getJSONArray(0);
        Coordinate[] coords = new Coordinate[ring.size()];

        for (int i = 0; i < ring.size(); i++) {
            JSONArray point = ring.getJSONArray(i);
            coords[i] = new Coordinate(point.getDouble(0), point.getDouble(1));
        }

        LinearRing shell = factory.createLinearRing(coords);
        return factory.createPolygon(shell);
    }

    private static SimpleFeature createFeature(
        SimpleFeatureType featureType,
        Polygon polygon,
        JSONObject properties) {

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);

        // 设置几何属性
        builder.add(polygon);

        // 设置其他属性
        builder.add(properties.getStr("countryEn", ""));
        builder.add(properties.getDouble("area", 0.0));
        builder.add(properties.getStr("sdate", ""));
        builder.add(properties.getStr("edate", ""));
        builder.add(properties.getInt("orbitalAltitude", 0));
        builder.add(properties.getStr("code", ""));
        builder.add(properties.getStr("noradNumber", ""));
        builder.add(properties.getStr("dataSource", ""));

        return builder.buildFeature(null);
    }

    private static void createShapefile(
        String shpPath,
        SimpleFeatureType featureType,
        SimpleFeatureCollection collection) throws Exception {

        File shpFile = new File(shpPath);
        ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();

        Map<String, Serializable> params = new HashMap<>();
        params.put("url", shpFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore dataStore =
            (ShapefileDataStore) factory.createNewDataStore(params);
        dataStore.createSchema(featureType);
        dataStore.setCharset(StandardCharsets.UTF_8);

        // 写入数据
        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);

        if (featureSource instanceof SimpleFeatureStore) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

            try (Transaction transaction = new DefaultTransaction("create")) {
                featureStore.setTransaction(transaction);
                featureStore.addFeatures(collection);
                transaction.commit();
            }
        }
        dataStore.dispose();
    }

    private static File createZipFile(Path tempDir, String baseName) throws IOException {
        // 创建ZIP文件
        File zipFile = Files.createTempFile(baseName, ".zip").toFile();

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 添加所有SHP相关文件到ZIP
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
                for (Path file : stream) {
                    if (!Files.isDirectory(file)) {
                        ZipEntry zipEntry = new ZipEntry(file.getFileName().toString());
                        zos.putNextEntry(zipEntry);
                        Files.copy(file, zos);
                        zos.closeEntry();
                    }
                }
            }
        }

        // 清理临时目录
        deleteDirectory(tempDir.toFile());

        return zipFile;
    }

    private static void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    // 使用示例
    public static void main(String[] args) {
        String geoJsonStr = "{\"features\":[{\"geometry\":{\"coordinates\":[[[118.17676248360395,29.406853890301747],[118.17647541204545,29.40564001119853],[118.14914321175723,29.289856690630057],[118.147622305,29.288813188],[118.132129171,29.286393453],[118.116714606,29.290548574],[118.110559634,29.296155484],[118.099749461,29.2873881],[118.072845502,29.293104356],[118.066587074,29.290867904],[118.075126909,29.268858817],[118.07027917,29.249902456],[118.07586101,29.245335681],[118.076562066,29.235741832],[118.067190501,29.218931904],[118.052643367,29.21739949],[118.046173011,29.220209143],[118.03718783,29.212950829],[118.023154612,29.171938032],[118.038731841,29.160109474],[118.038135128,29.141125719],[118.033244535,29.129853523],[118.047841749,29.11894599],[118.047173244,29.115290115],[118.037639485,29.112569433],[118.032799265,29.100038152],[118.040657107,29.091590774],[118.070499118,29.077258564],[118.071609266,29.065545929],[118.06170718,29.056650844],[118.061328645,29.051445685],[118.065963052,29.039067245],[118.074768537,29.039620967],[118.082746836,29.032132605],[118.08487675391287,29.016873266178184],[118.05828911042865,28.90363776398437],[118.02887025026803,28.778122449310963],[117.99950165249794,28.652601052845423],[117.97018234847494,28.5270710830312],[117.94091314306549,28.401537627975472],[117.91169307497913,28.27599819619994],[117.88252118591815,28.150450296227152],[117.8533982837925,28.02489901562627],[117.8317749947206,27.93152082609935],[117.831340816,27.931120003],[117.808764901,27.923026002],[117.8010181,27.909791201],[117.781921489,27.899108481],[117.783293054,27.886186839],[117.777423567,27.869305277],[117.782375725,27.857613561],[117.772705383,27.841702094],[117.757243114,27.833481567],[117.749253332,27.815319099],[117.73545173,27.80375741],[117.728420205,27.804957642],[117.711745619,27.817510084],[117.699242264,27.837195233],[117.689116465,27.829457836],[117.68234533,27.831007616],[117.675255035,27.8273368],[117.668154146,27.831807624],[117.660593304,27.846701175],[117.621435564,27.866349869],[117.61142114889654,27.866347245566644],[117.6294667461969,27.944407137400056],[117.65855836083072,28.070036635952714],[117.68769816056712,28.195660794419595],[117.71688710132838,28.32128211154526],[117.74612437609096,28.44689550077565],[117.77541094507858,28.572503459029498],[117.80474777048761,28.698108478802887],[117.83413404942561,28.823705477539054],[117.86357075197067,28.949296949983214],[117.89305825814627,29.074882862539916],[117.9225975491509,29.200465711300076],[117.9521878235316,29.326040411359692],[117.98183006675698,29.451609457222677],[118.01137400884627,29.57653574493213],[118.020971283,29.57608479],[118.035317237,29.565820636],[118.049773339,29.542930056],[118.08457234,29.537325681],[118.097010436,29.540361169],[118.102630971,29.536530568],[118.100907286,29.530309953],[118.105126238,29.525127507],[118.127413222,29.513052525],[118.139147482,29.492276353],[118.119466192,29.474297807],[118.120378106,29.468731863],[118.129657573,29.467265574],[118.132804174,29.446910142],[118.122823235,29.440639315],[118.121470397,29.432691393],[118.144268224,29.415222005],[118.151743908,29.415832156],[118.174100811,29.406358157],[118.17676248360395,29.406853890301747]]],\"type\":\"Polygon\"},\"type\":\"Feature\",\"properties\":{\"countryEn\":\"China\",\"area\":\"3504.7445\",\"country\":\"中国\",\"sdate\":\"2025-10-25 06:27:04\",\"code\":\"CFOSAT\",\"num\":2,\"noradNumber\":\"43662\",\"orbitalAltitude\":600000,\"sort\":4,\"type\":\"国产公益卫星\",\"edate\":\"2025-10-25 06:29:18\",\"countryCode\":\"CHN\",\"id\":\"148\",\"line3\":\"2 43662  97.3644 236.8443 0001564  80.0276 280.1134 15.16320121376338\",\"dataSource\":\"中法海洋卫星\",\"line2\":\"1 43662U 18083A   25228.36520001  .00003558  00000-0  18692-3 0  9997\",\"sideSwingAngle\":0,\"line1\":\"CFOSAT\",\"bjsdate\":\"2025-10-25 06:27:04\",\"bjedate\":\"2025-10-25 06:29:18\"}}],\"type\":\"FeatureCollection\"}";

        try {
            File zipFile = generateShpZipFromGeoJson(geoJsonStr, "satellite_coverage");
            System.out.println("ZIP文件生成成功: " + zipFile.getAbsolutePath());

            // 在实际应用中，可以将此文件发送给前端下载
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
