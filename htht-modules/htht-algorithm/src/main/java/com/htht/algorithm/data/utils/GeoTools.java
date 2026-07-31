package com.htht.algorithm.data.utils;

import com.htht.algorithm.data.core.entity.PGDatastore;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.geotools.data.DataStore;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

@Slf4j
public class GeoTools {

    private static DataStore postgisDataStore = PGDatastore.getDefaultDatastore();

    /**
     * 通用，要素集写入postgis
     *
     * @param featureCollection
     * @param pgTableName
     * @return
     */
    public static boolean write2pg(FeatureCollection featureCollection, String pgTableName) {
        boolean result = false;

        try {
            if (Objects.isNull(featureCollection) || Strings.isEmpty(pgTableName)) {
                log.error("参数无效");
                return result;
            }
            SimpleFeatureType simpleFeatureType = (SimpleFeatureType) featureCollection.getSchema();
            SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
            typeBuilder.init(simpleFeatureType);
            typeBuilder.setName(pgTableName);
            SimpleFeatureType newType = typeBuilder.buildFeatureType();
            postgisDataStore.createSchema(newType);
            FeatureIterator iterator = featureCollection.features();
            FeatureWriter<SimpleFeatureType, SimpleFeature> featureWriter = postgisDataStore.getFeatureWriterAppend(pgTableName, Transaction.AUTO_COMMIT);
            while (iterator.hasNext()) {
                Feature feature = iterator.next();
                SimpleFeature simpleFeature = featureWriter.next();
                Collection<Property> properties = feature.getProperties();
                Iterator<Property> propertyIterator = properties.iterator();
                while (propertyIterator.hasNext()) {
                    Property property = propertyIterator.next();
                    simpleFeature.setAttribute(property.getName().toString(), property.getValue());
                }
                featureWriter.write();
            }
            iterator.close();
            featureWriter.close();
            return true;
        } catch (IOException e) {
            log.error("失败", e);
        }
        return false;
    }

    /**
     * shpfile文件导入到postgis中
     *
     * @param shpPath
     * @param pgTableName
     * @param shpCharset
     * @return
     */
    public static boolean shp2pgTable(String shpPath, String pgTableName, Charset shpCharset) {
        deletePgTable(pgTableName);
        boolean result = false;
        try {
            log.info("pg表入库开始：" + pgTableName);
            ShapefileDataStore shapefileDataStore = new ShapefileDataStore(new File(shpPath).toURI().toURL());
            shapefileDataStore.setCharset(shpCharset);
            FeatureCollection featureCollection = shapefileDataStore.getFeatureSource().getFeatures();
            result = write2pg(featureCollection, pgTableName);
            log.info("pg表入库结束：" + pgTableName);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    public static boolean deletePgTable(String pgTableName) {
        try {
            if (postgisDataStore.getSchema(pgTableName) != null) {
                log.info("开始删除pg表：" + pgTableName);
                postgisDataStore.removeSchema(pgTableName);
                log.info("结束删除pg表：" + pgTableName);
            }
            return true;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    public static void main(String[] args) {
        shp2pgTable("D:\\project\\xjy\\data\\土地利用类型\\LUCC2020_30m\\土地利用类型30米.shp", "shp_1664659424091344897", Charset.forName("GBK"));
    }
}
