package com.htht.algorithm.data.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.htht.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.logging.log4j.util.Strings;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 验证工具类
 *
 * @author wjx
 */
@Slf4j
public class SentinelUtil {

    //基础请求地址
    private final static String BASE_URL = "https://services.sentinel-hub.com";

    //鉴权方式
    private final static String GRANT_TYPE = "client_credentials";
    //客户端ID
    //private final static String CLIENT_ID = "fd885033-fb05-4fea-b316-d575e8050bde";//胡博士账号
    private final static String CLIENT_ID = "3c8dfbaf-810b-4a50-9ee4-21d75de953ee";//郭博士账号
    //客户端密码
    //private final static String CLIENT_SECRET = "1vnJj[HOB?XplP!6c}6M:)mXDkKVuFc6+/^sl}tA";//胡博士账号
    private final static String CLIENT_SECRET = "Wsubpz5WEQJtoBgBlMu0J0hrM32N7p1C";//郭博士账号
    //WMTS服务实例ID
    private final static String INSTANCE_ID = "a47cf3cc-0613-45b9-ba6f-a0d23cab73d5";

    //token有效时长
    private final static Long INVALID_TIME = 20 * 60 * 1000L;

    private static Map<String, Object> tokenMap = new HashMap<>();

    private final static DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private final static DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 得到token
     *
     * @return
     */
    private static String getToken() {
        if (StringUtils.isNotBlank((String) tokenMap.get("token")) && (Long) tokenMap.get("time") > System.currentTimeMillis()) {
            return (String) tokenMap.get("token");
        }
        String result = OkHttpUtil.builder()
            .url(BASE_URL + "/oauth/token")
            .addParam("client_id", CLIENT_ID)
            .addParam("client_secret", CLIENT_SECRET)
            .addParam("grant_type", GRANT_TYPE)
            .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
            .post(false)
            .sync();

        try {
            JSONObject obj = JSONObject.parseObject(result);
            String token = obj.getString("access_token");
            tokenMap.put("token", "Bearer " + token);
            tokenMap.put("time", INVALID_TIME + System.currentTimeMillis());
            log.info("token----------->" + token);
            return "Bearer " + token;
        } catch (Exception e) {
            log.error("请求token出错" + e.getMessage());
            return null;
        }
    }

    /**
     * 数据搜索
     *
     * @param api
     * @param intersects GeoJson,替换bbox
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param collection 卫星列表
     * @param limit      返回条数
     * @return
     */
    public static String search(String api, String intersects, Date startTime, Date endTime, String collection, Integer limit, Integer next, String filter) {
//        System.out.println("start search");
        String result = OkHttpUtil.builder()
            .url(api + "/api/v1/catalog/1.0.0/search")
            .addHeader("Authorization", getToken())
            .addHeader("Content-Type", "application/json")
            .addParam(Strings.isNotBlank(intersects), "intersects", JSONObject.parseObject(intersects))
            //如果没有指定范围，则查询全球的数据
            .addParam(Strings.isBlank(intersects), "bbox", new double[]{-180, -90, 180, 90})
            .addParam("datetime", df.format(startTime) + "/" + df.format(endTime))
            .addParam("collections", new String[]{collection})
            .addParam("limit", limit)
            .addParam(next > 0, "next", next)
            .addParam(Strings.isNotBlank(filter), "filter", filter)
            .post(true)
            .sync();
        log.info("请求结果：" + result);
//        System.out.println("qingqiujjieguo：" + result);
        return result;
    }

    private static double[] transform(Double[] bbox) {
        SpatialReference inSpatialReference = new SpatialReference();
        inSpatialReference.ImportFromEPSG(4326);

        SpatialReference outSpatialReference = new SpatialReference();
        outSpatialReference.ImportFromEPSG(3857);
        CoordinateTransformation transformation = new CoordinateTransformation(inSpatialReference, outSpatialReference);
        double[] p1 = transformation.TransformPoint(bbox[0], bbox[1]);
        double[] p2 = transformation.TransformPoint(bbox[2], bbox[3]);
        return new double[]{p1[0], p1[1], p2[0], p2[1]};
    }

    /**
     * 目录API条目页面
     *
     * @return
     */
    public static String catalog() {
        return OkHttpUtil.builder()
            .url(BASE_URL + "/api/v1/catalog/")
            .get()
            .sync();
    }

    public static String datasets() {
        return OkHttpUtil.builder()
            .url(BASE_URL + "/configuration/v1/datasets")
            .addHeader("Authorization", getToken())
            .get()
            .sync();
    }

    /**
     * 列出所有可用的集合
     *
     * @return
     */
    public static String allCollections() {
        return OkHttpUtil.builder()
            .url("https://services-uswest2.sentinel-hub.com/api/v1/catalog/1.0.0/collections")
            .addHeader("Authorization", getToken())
            .get()
            .sync();
    }

    /**
     * 列出特定类型的可用的集合
     *
     * @param type
     * @return
     */
    public static String collections(String type) {
        return OkHttpUtil.builder()
            .url(BASE_URL + "/api/v1/catalog/1.0.0/collections/" + type + "/")
            .addHeader("Authorization", getToken())
            .get()
            .sync();

    }

    /**
     * 列出特定类型的所有过滤器参数
     *
     * @param collection
     * @return
     */
    public static String queryAbles(String collection) {
        return OkHttpUtil.builder()
            .url(BASE_URL + "/api/v1/catalog/1.0.0/collections/" + collection + "/queryables")
            .addHeader("Authorization", getToken())
            .get()
            .sync();

    }

    /**
     * 获取WMTS切片
     *
     * @param layerId
     * @param format
     * @param tileMatrix
     * @param tileRow
     * @param tileCol
     * @return
     */
    public static InputStream getTile(String api, String instanceId, String layerId, String format, int tileMatrix, int tileRow, int tileCol, String startTime, String endTime) throws IOException {
        //经纬度转墨卡托坐标
        Response response = OkHttpUtil.builder()
            .url(api + "/ogc/wmts/" + instanceId)
            .addParam("REQUEST", "GetTile")
            .addParam("TILEMATRIXSET", "PopularWebMercator256")
            .addParam("LAYER", layerId)
            .addParam("TILEMATRIX", tileMatrix)
            .addParam("TILEROW", tileRow)
            .addParam("TILECOL", tileCol)
            .addParam("TIME", startTime + "/" + endTime)
            .addParam("FORMAT", format)
            .get()
            .response();
        if (response.code() != 200) {
            log.error(response.body().string());
            return null;
        } else {
            return response.body().byteStream();
        }
    }

}
