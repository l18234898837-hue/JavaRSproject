package com.htht.business.utils.shp;


import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.geotools.data.DataStore;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class   ShapeUtil {
    private static int BUFFER = 1024;
    private static final String[] SHP_FILES = new String[]{
            ".dbf",
            ".prj",
            ".shp",
            ".shx",
            ".fix"
    };

    public static final Charset GBK = Charset.forName("gbk");

    /**
     * 生成shape文件
     *
     * @param shpPath 生成shape文件路径（包含文件名称）
     * @param encode 编码
     * @param geoType 图幅类型，Point和Rolygon
     * @param geoms 图幅集合
     */
    public static void write2Shape(String shpPath, String encode, String geoType, List<Geometry> geoms) {
        try {
            //创建shape文件对象
            File file = new File(shpPath);
            Map<String, Serializable> params = new HashMap<>();
            params.put(ShapefileDataStoreFactory.URLP.key, file.toURI().toURL());
            ShapefileDataStore ds = (ShapefileDataStore) new ShapefileDataStoreFactory().createNewDataStore(params);
            //定义图形信息和属性信息
            SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
            tb.setCRS(DefaultGeographicCRS.WGS84);
            tb.setName("shapefile");

            if ("Polygon".equals(geoType)) {
                tb.add("the_geom", Polygon.class);
            } else if ("MultiPolygon".equals(geoType)) {
                tb.add("the_geom", MultiPolygon.class);
            } else if ("Point".equals(geoType)) {
                tb.add("the_geom", Point.class);
            } else if ("MultiPoint".equals(geoType)) {
                tb.add("the_geom", MultiPoint.class);
            } else if ("LineString".equals(geoType)) {
                tb.add("the_geom", LineString.class);
            } else if ("MultiLineString".equals(geoType)) {
                tb.add("the_geom", MultiLineString.class);
            } else {
                throw new Exception("Geometry中没有该类型：" + geoType);
            }

            ds.createSchema(tb.buildFeatureType());
            //设置编码
            Charset charset = Charset.forName(encode);
            ds.setCharset(charset);
            //设置Writer
            String[] typeNames = ds.getTypeNames();
            FeatureWriter<SimpleFeatureType, SimpleFeature> writer = ds.getFeatureWriter(typeNames[0], Transaction.AUTO_COMMIT);
            for (Geometry geom : geoms) {
                //String type = geom.getGeometryType();
                //写下一条
                SimpleFeature feature = writer.next();
                feature.setAttribute("the_geom", geom);


            }
            writer.write();
            writer.close();
            ds.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Feature> shpZip2Feature(File zipFile, Charset charset) {
        try {
            String shpPath = unzipShp(zipFile, zipFile.getParent(), charset);
            return shp2Feature(new File(shpPath), GBK);
        } catch (IOException e) {
            throw new RuntimeException("读取shp数据失败");
        }
    }

    /**
     * 读取shp文件
     */
    public static List<Feature> shp2Feature(File file, Charset charset) throws IOException {
        List<Feature> resultList = new LinkedList<>();
        DataStore dataStore = buildDataStore(file, charset);
        try {
            if (dataStore == null) {
                return null;
            }
            String[] typeNames = dataStore.getTypeNames();
            for (String typeName : typeNames) {
                FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
//                CoordinateReferenceSystem crs = source.getInfo().getCRS();
                FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures();
                FeatureIterator<SimpleFeature> features = collection.features();
                while (features.hasNext()) {
                    SimpleFeature simpleFeature = features.next();
                    Collection<Property> attributes = simpleFeature.getProperties();
                    Feature feature = new Feature();
                    Map<String, Object> properties = feature.getProperties();
                    for (Property attribute : attributes) {
                        String name = attribute.getName().getLocalPart();
                        if (name.equals("the_geom")) {
                            String wkt = attribute.getValue().toString();
                            feature.setWkt(wkt);
                        } else {
                            properties.put(name, attribute.getValue());
                        }
                    }
                    resultList.add(feature);
                }
                // 关闭文件流
                features.close();
            }
            dataStore.dispose();
        } catch (Exception e) {
            log.error("读取shp出现异常：", e);
            throw new RuntimeException(e.getMessage());
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
        }
        return resultList;
    }

    public static void feature2ShpPoint(String shpPath, Charset charset, List<Feature> sourceFeatures) throws IOException, ParseException {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("shapefile");
        builder.setCRS(DefaultGeographicCRS.WGS84);
        builder.add("the_geom", Point.class);
        feature2ShpAll(shpPath, charset, sourceFeatures, builder);
    }


    public static void feature2Shp(String shpPath, Charset charset, List<Feature> sourceFeatures) throws IOException, ParseException {
        feature2Shp(shpPath, charset, sourceFeatures, sourceFeatures.get(0).getGeometry().getClass());
    }

    public static void feature2Shp(String shpPath, Charset charset, List<Feature> sourceFeatures, Class<? extends Geometry> clazz) throws IOException, ParseException {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("shapefile");
        builder.setCRS(DefaultGeographicCRS.WGS84);
        builder.add("the_geom", clazz);
        feature2ShpAll(shpPath, charset, sourceFeatures, builder);
        Files.write(Path.of(shpPath.substring(0, shpPath.length() - 3) + "cpg"), charset.name().getBytes());
    }

    private static void feature2ShpAll(String shpPath, Charset charset, List<Feature> sourceFeatures, SimpleFeatureTypeBuilder builder) throws IOException, ParseException {
        File shpFile = new File(shpPath);
        File parentFile = shpFile.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        if (!sourceFeatures.isEmpty()) {
            Feature feature = sourceFeatures.get(0);
            Map<String, Object> properties = feature.getProperties();
            Set<Map.Entry<String, Object>> entries = properties.entrySet();
            for (Map.Entry<String, Object> entry : entries) {
                Object value = entry.getValue();
                builder.add(entry.getKey(), value != null ? value.getClass() : Object.class);
            }
        }

        SimpleFeatureType type = builder.buildFeatureType();

        Map<String, Serializable> params = new HashMap<>();
        params.put("url", shpFile.toURI().toURL());
        params.put("charset", charset.name());
//        params.put("create spatial index", Boolean.TRUE);
        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
        dataStore.createSchema(type);
        dataStore.setCharset(charset);

        FeatureWriter<SimpleFeatureType, SimpleFeature> featureWriter = dataStore.getFeatureWriter(dataStore.getTypeNames()[0], Transaction.AUTO_COMMIT);
        for (Feature feature : sourceFeatures) {
            Geometry geometry = feature.getGeometry();
            SimpleFeature simpleFeature = featureWriter.next();
            simpleFeature.setAttribute("the_geom", geometry);
            Map<String, Object> properties = feature.getProperties();
            Set<Map.Entry<String, Object>> entries = properties.entrySet();
            for (Map.Entry<String, Object> entry : entries) {
                simpleFeature.setAttribute(entry.getKey(), entry.getValue());
            }
            featureWriter.write();
        }
        featureWriter.close();
        dataStore.dispose();
    }

    private static Geometry transform(Geometry geometry, CoordinateReferenceSystem sourceCRS, CoordinateReferenceSystem targetCRS) {
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
     * 生成shape文件
     *
     * @param shpPath 生成shape文件路径（包含文件名称）
     * @param encode 编码
     * @param geoType 图幅类型，Point和Rolygon
     * @param shpKey data中图幅的key
     * @param attrKeys 属性key集合
     * @param data 图幅和属性集合
     */
    public static void write2Shape(String shpPath, String encode, String geoType, String shpKey, List<String> attrKeys, List<Map<String, Object>> data) {
        try {
            if (data == null || data.size() == 0) {
                return;
            }
            //创建shape文件对象
            File file = new File(shpPath);
            Map<String, Serializable> params = new HashMap<>();
            params.put(ShapefileDataStoreFactory.URLP.key, file.toURI().toURL());
            ShapefileDataStore ds = (ShapefileDataStore) new ShapefileDataStoreFactory().createNewDataStore(params);

            //定义图形信息和属性信息
            SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
            tb.setCRS(DefaultGeographicCRS.WGS84);
            tb.setName("shapefile");

            if ("Polygon".equals(geoType)) {
                tb.add("the_geom", Polygon.class);
            } else if ("MultiPolygon".equals(geoType)) {
                tb.add("the_geom", MultiPolygon.class);
            } else if ("Point".equals(geoType)) {
                tb.add("the_geom", Point.class);
            } else if ("MultiPoint".equals(geoType)) {
                tb.add("the_geom", MultiPoint.class);
            } else if ("LineString".equals(geoType)) {
                tb.add("the_geom", LineString.class);
            } else if ("MultiLineString".equals(geoType)) {
                tb.add("the_geom", MultiLineString.class);
            } else {
                throw new Exception("Geometry中没有该类型：" + geoType);
            }

            for (String field : attrKeys) {
                tb.add(field, String.class);
            }
//            tb.add(shpKey, org.gdal.ogr.Geometry.class);
            ds.createSchema(tb.buildFeatureType());
            //设置编码
            Charset charset = Charset.forName(encode);
            ds.setCharset(charset);
            //设置Writer
            FeatureWriter<SimpleFeatureType, SimpleFeature> writer = ds.getFeatureWriter(ds.getTypeNames()[0], Transaction.AUTO_COMMIT);
            //写入文件信息
            for (int i = 0; i < data.size(); i++) {
                SimpleFeature feature = writer.next();
                Map<String, Object> row = data.get(i);
                Geometry geom = (Geometry) row.get(shpKey);
                feature.setAttribute("the_geom", geom);
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (!entry.getKey().equals(shpKey)) {
                        if (entry.getValue() != null) {
                            feature.setAttribute(entry.getKey(), entry.getValue());
                        } else {
                            feature.setAttribute(entry.getKey(), "");
                        }
                    }
                }
            }
            writer.write();
            writer.close();
            ds.dispose();

            //添加到压缩文件
            //zipShapeFile(shpPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String zipShapeFile(String shpPath) {
        return zipShapeFile(shpPath, true);
    }

    /**
     * 压缩shape文件
     *
     * @param shpPath shape文件路径（包含shape文件名称）
     */
    public static String zipShapeFile(String shpPath, boolean removeSourceFile) {
        File shpFile = new File(shpPath);
        String shpRoot = shpFile.getParentFile().getPath();
        String shpName = shpFile.getName().substring(0, shpFile.getName().lastIndexOf("."));
        String zipPath = shpRoot + File.separator + shpName + ".zip";
        File zipFile = new File(zipPath);
        InputStream input = null;
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile));) {
            // zip的名称为
            zipOut.setComment(shpName);
            byte[] bytes = new byte[8096];
            for (String suffix : SHP_FILES) {
                String path = shpRoot + File.separator + shpName + suffix;
                File file = new File(path);
                if (!file.exists()) {
                    continue;
                }
                input = new FileInputStream(file);
                zipOut.putNextEntry(new ZipEntry(file.getName()));
                int temp = 0;
                while ((temp = input.read(bytes)) != -1) {
                    zipOut.write(bytes, 0, temp);
                }
                input.close();
                if (removeSourceFile) {
                    file.delete();
                }
            }
            return zipPath;
        } catch (Exception e) {
            throw new RuntimeException("打包shp文件出错");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void removeShp(String shpPath) {
        File shpFile = new File(shpPath);
        String shpRoot = shpFile.getParentFile().getPath();
        String shpName = shpFile.getName().substring(0, shpFile.getName().lastIndexOf("."));
        for (String suffix : SHP_FILES) {
            String path = shpRoot + File.separator + shpName + suffix;
            File file = new File(path);
            if (!file.exists()) {
                continue;
            }
            boolean delete = file.delete();
            if (!delete) {
                throw new RuntimeException("删除失败:" + path);
            }
        }
    }

    public static String unzipShp(File file, String destDirPath, Charset charset) throws IOException {
        ZipFile zipfile = new ZipFile(file, charset);
        Enumeration<?> e = zipfile.entries();
        File dir = new File(destDirPath);
        dir.mkdirs();
        String unzipPath = dir.getAbsolutePath();
        String shpFilePath = null;
        while (e.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) e.nextElement();
            String name = entry.getName().toLowerCase();
            String filePath = destDirPath + File.separator + name;
            if (entry.isDirectory()) {
                String archiveEntryPath = unzipPath + File.separator + entry.getName();
                File file1 = new File(archiveEntryPath);
                if (!file1.exists()) {
                    file1.mkdirs();
                }
            } else {
                if (name.endsWith(".shp")) {
                    shpFilePath = filePath;
                }
                unzipEntry(unzipPath + File.separator + name, zipfile, entry);
            }
        }
        zipfile.close();
        return shpFilePath;
    }

    private static void unzipEntry(String zipPath, ZipFile zipfile, ZipEntry entry) {
        byte[] dataByte = new byte[BUFFER];
        BufferedOutputStream dest = null;
        BufferedInputStream is = null;
        try {
            is = new BufferedInputStream(zipfile.getInputStream(entry));
            int count;
            File imageFile = new File(zipPath);
            FileOutputStream fos = new FileOutputStream(imageFile);
            dest = new BufferedOutputStream(fos, BUFFER);
            while ((count = is.read(dataByte, 0, BUFFER)) != -1) {
                dest.write(dataByte, 0, count);
            }
            dest.flush();
            dest.close();
            is.close();
        } catch (Exception exception) {
            exception.printStackTrace();
        } finally {
            try {
                if (dest != null) {
                    dest.close();
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public static String unzip(String zipFilePath, String destDirPath) throws IOException {
        String shpFilePath = null;
        try (FileInputStream fis = new FileInputStream(zipFilePath);
             GZIPInputStream gis = new GZIPInputStream(fis);
             TarArchiveInputStream taris = new TarArchiveInputStream(gis)) {
            TarArchiveEntry entry = null;
            while ((entry = taris.getNextTarEntry()) != null) {
                String name = entry.getName().toLowerCase();
                String filePath = destDirPath + File.separator + name;
                if (!entry.isDirectory()) {
                    if (name.endsWith(".shp")) {
                        shpFilePath = filePath;
                    }
                    File entryFile = new File(filePath, entry.getName());
                    OutputStream out = new FileOutputStream(entryFile);
                    IOUtils.copy(taris, out);
                    out.close();
                } else {
                    File dir = new File(filePath);
                    dir.mkdir();
                }
            }
            //如果需要刪除之前解压的gz文件，在这里进行

        } catch (Exception e) {
            e.printStackTrace();
        }
        return shpFilePath;
    }

    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
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


}
