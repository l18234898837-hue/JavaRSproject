package com.htht.business.utils.shp;

import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

import java.util.HashMap;
import java.util.Map;

public class Feature {
    private String wkt;
    private Map<String, Object> properties = new HashMap<>();
    private Coordinate[] bound = new Coordinate[2];

    private Geometry geometry;

    private static final WKTReader2 WKT_READER = new WKTReader2();

    public String getWkt() {
        return wkt;
    }

    public void setWkt(String wkt) {
        this.wkt = wkt;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Coordinate[] getBound() {
        return bound;
    }

    public void setBound(Coordinate[] bound) {
        this.bound = bound;
    }

    public Geometry getGeometry() throws ParseException {
        if (geometry == null) {
            geometry = WKT_READER.read(wkt);
        }
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    @Override
    public String toString() {
        return "Feature{" +
                "wkt='" + wkt + '\'' +
                ", properties=" + properties +
                '}';
    }
}
