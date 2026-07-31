package com.htht.algorithm.data.tile.vector.utils;

import com.htht.common.exception.UtilException;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.data.tile.vector.entity.ShpInfo;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.gdal;
import org.gdal.ogr.*;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.util.*;

/**
 * shp操作工具类
 *
 * @author wjx
 * @since 2023-4-8
 */
@Slf4j
public class ShpUtil {

    private static final String SHP_DRIVER_NAME = "ESRI Shapefile";
    private static final String GEO_JSON_DRIVER_NAME = "GeoJSON";

    private static final int WGS_84_EPSG_CODE = 4326;

    static {
        //注册
        gdal.AllRegister();
    }


    /**
     * 根据路径得到shp信息
     *
     * @param path
     * @return
     */
    public static ShpInfo getInfo(String path) {
        boolean exist = FileUtils.exist(path);
        if (!exist) {
            throw new UtilException("shp文件不存在：" + path);
        }
        setConfig();
        Driver driver = getDriver();
        DataSource dataSource = driver.Open(path);
        if (dataSource == null) {
            throw new UtilException("读取shp失败:" + path);
        }
        Layer layer = dataSource.GetLayer(0);
        String layerName = layer.GetName();
        double[] layerExtent = layer.GetExtent();
        double xMin = layerExtent[0];
        double xMax = layerExtent[1];
        double yMin = layerExtent[2];
        double yMax = layerExtent[3];
        SpatialReference spatialReference = layer.GetSpatialRef();

        CoordinateTransformation wgs842ProjTransformation = wgs842ProjTransformation(spatialReference.ExportToWkt());
        CoordinateTransformation proj2Wgs84Transformation = proj2Wgs84Transformation(spatialReference.ExportToWkt());

        double[] lngLatMin = proj2Wgs84Transformation.TransformPoint(xMin, yMin);
        double[] lngLatMax = proj2Wgs84Transformation.TransformPoint(xMax, yMax);
        double lngMin = lngLatMin[0];
        double latMin = lngLatMin[1];
        double lngMax = lngLatMax[0];
        double latMax = lngLatMax[1];

        FeatureDefn featureDefn = layer.GetLayerDefn();
        int fieldCount = featureDefn.GetFieldCount();
        long featureCount = layer.GetFeatureCount();
        Map<String, String> fieldMap = new HashMap<>();
        for (int i = 0; i < fieldCount; i++) {
            FieldDefn fieldDefn = featureDefn.GetFieldDefn(i);
            //得到属性字段类型
            int fieldType = fieldDefn.GetFieldType();
            String fieldTypeName = fieldDefn.GetFieldTypeName(fieldType);
            //得到属性字段名称
            String fieldName = fieldDefn.GetName();
            fieldMap.put(fieldName, fieldTypeName);
        }
        dataSource.FlushCache();
        dataSource.delete();
        return new ShpInfo(layerName, spatialReference, xMin, yMin, xMax, yMax, lngMin, latMin, lngMax, latMax, featureCount, fieldCount, fieldMap, layer, wgs842ProjTransformation, proj2Wgs84Transformation);
    }

    /**
     * 读取图层要素信息
     *
     * @param shpInfo
     * @return
     */
    public static List<Map<String, Object>> readFeatures(ShpInfo shpInfo) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (int i = 0; i < shpInfo.getFeatureCount(); i++) {
            Feature feature = shpInfo.getLayer().GetFeature(i);
            Map<String, Object> featureMap = new HashMap<>();
            Iterator<String> keyIterator = shpInfo.getFieldMap().keySet().iterator();
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                Object value = feature.GetFieldAsString(key);
                featureMap.put(key, value);
            }
            resultList.add(featureMap);
        }
        return resultList;
    }

    private static void setConfig() {
        //路径支持中文路径
        gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", "YES");
        //属性表字段支持中文
        gdal.SetConfigOption("SHAPE_ENCODING", "CP936");

    }

    private static Driver getDriver() {
        Driver driver = ogr.GetDriverByName(SHP_DRIVER_NAME);
        if (driver == null) {
            throw new UtilException("GDAL获取shp驱动错误");
        }
        return driver;
    }

    /**
     * 经纬度坐标转投影坐标转换器
     *
     * @param proj
     * @return
     */
    private static CoordinateTransformation wgs842ProjTransformation(String proj) {
        SpatialReference inSpatialReference = new SpatialReference();
        inSpatialReference.ImportFromEPSG(WGS_84_EPSG_CODE);

        SpatialReference outSpatialReference = new SpatialReference();
        outSpatialReference.ImportFromWkt(proj);
        return new CoordinateTransformation(inSpatialReference, outSpatialReference);
    }

    /**
     * 投影坐标转经纬度坐标转化器
     *
     * @param proj
     * @return
     */
    private static CoordinateTransformation proj2Wgs84Transformation(String proj) {
        SpatialReference inSpatialReference = new SpatialReference();
        inSpatialReference.ImportFromWkt(proj);

        SpatialReference outSpatialReference = new SpatialReference();
        outSpatialReference.ImportFromEPSG(WGS_84_EPSG_CODE);
        return new CoordinateTransformation(inSpatialReference, outSpatialReference);
    }

    /**
     * 将shp转为geoJson
     *
     * @param shpPath
     * @param jsonPath
     */
    public static void shp2json(String shpPath, String jsonPath) {
        if (!new File(jsonPath).exists()) {
            log.info("shp转geoJson开始：{}", shpPath);
            DataSource ds = ogr.Open(shpPath, 0);
            Driver driver = ogr.GetDriverByName(GEO_JSON_DRIVER_NAME);
            driver.CopyDataSource(ds, jsonPath);
            ds.FlushCache();
            ds.delete();
            log.info("shp转geoJson结束：{}", shpPath);
        }
    }

    public static String createWkt(double lngMin, double lngMax, double latMin, double latMax) {
        StringBuffer sb = new StringBuffer();
        sb.append("POLYGON((");
        sb.append(lngMin + " " + latMin + ",");
        sb.append(lngMin + " " + latMax + ",");
        sb.append(lngMax + " " + latMax + ",");
        sb.append(lngMax + " " + latMin + ",");
        sb.append(lngMin + " " + latMin + "))");
        return sb.toString();
    }

    /**
     * geoJSON转wkt
     *
     * @param json
     * @return
     */
    public static String json2wkt(String json) {
        return Geometry.CreateFromJson(json).ExportToWkt();
    }

    /**
     * wkt转geoJSON
     *
     * @param wkt
     * @return
     */
    public static String wkt2json(String wkt) {
        return Geometry.CreateFromWkt(wkt).ExportToJson();
    }

    public static void main(String[] args) {
//        getInfo("D:\\project\\水上水下一体化项目\\data\\广东珠江口中华白海豚国家级自然保护区边界范围图\\广东珠江口中华白海豚国家级自然保护区.shp");
        ogr.RegisterAll();
    }
}
