package com.htht.algorithm.data.transform;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/12/22 10:02
 **/
public class CoordinateTransform {

    private static final double PI = 3.1415926535897932384626;
    private static final double A = 6378245.0; // WGS-84椭球体长半轴
    private static final double EE = 0.00669342162296594323; // WGS-84椭球偏心率平方

    // 1. WGS-84 转 GCJ-02 (火星坐标系)
    public static double[] wgs84ToGcj02(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        double mgLat = lat + dLat;
        double mgLng = lng + dLng;
        return new double[]{mgLng, mgLat}; // 注意：经度在前，纬度在后
    }

    // 2. GCJ-02 转 WGS-84 （近似方法，精度约1-2米）
    public static double[] gcj02ToWgs84(double lng, double lat) {
        double[] gcj = wgs84ToGcj02(lng, lat);
        // 通过计算与加密坐标的差值，反算原始坐标
        return new double[]{lng * 2 - gcj[0], lat * 2 - gcj[1]};
    }

    // 3. GCJ-02 转 BD-09 （百度坐标系）
    public static double[] gcj02ToBd09(double lng, double lat) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * PI);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * PI);
        double bdLng = z * Math.cos(theta) + 0.0065;
        double bdLat = z * Math.sin(theta) + 0.006;
        return new double[]{bdLng, bdLat};
    }

    // 4. BD-09 转 GCJ-02
    public static double[] bd09ToGcj02(double lng, double lat) {
        double x = lng - 0.0065;
        double y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * PI);
        double ggLng = z * Math.cos(theta);
        double ggLat = z * Math.sin(theta);
        return new double[]{ggLng, ggLat};
    }

    // 5. 链式转换：WGS-84 转 BD-09
    public static double[] wgs84ToBd09(double lng, double lat) {
        double[] gcj = wgs84ToGcj02(lng, lat);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    // 6. 链式转换：BD-09 转 WGS-84
    public static double[] bd09ToWgs84(double lng, double lat) {
        double[] gcj = bd09ToGcj02(lng, lat);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    // 判断坐标是否在中国以外（国内坐标才需要加密）
    private static boolean outOfChina(double lng, double lat) {
        if (lng < 72.004 || lng > 137.8347) {
            return true;
        }
        return lat < 0.8293 || lat > 55.8271;
    }

    // 纬度转换辅助方法
    private static double transformLat(double lng, double lat) {
        double ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lat * PI) + 40.0 * Math.sin(lat / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(lat / 12.0 * PI) + 320 * Math.sin(lat * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    // 经度转换辅助方法
    private static double transformLng(double lng, double lat) {
        double ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lng * PI) + 40.0 * Math.sin(lng / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(lng / 12.0 * PI) + 300.0 * Math.sin(lng / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    // 测试方法
    public static void main(String[] args) {
        // 以天安门广场的WGS-84坐标为例
        double lng = 116.397428;
        double lat = 39.90923;

        System.out.println("原始WGS84坐标: " + lng + ", " + lat);

        double[] gcj = wgs84ToGcj02(lng, lat);
        System.out.println("转换为GCJ02 (高德): " + gcj[0] + ", " + gcj[1]);

        double[] wgs = gcj02ToWgs84(gcj[0], gcj[1]);
        System.out.println("转回WGS84: " + wgs[0] + ", " + wgs[1]);
    }
}
