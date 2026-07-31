package com.htht.algorithm.data.utils.dataDownload.UsgsDownload;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/8/29 16:32
 **/
public class UsgsSearch {
    private static String getToken(){
        String response = UsgsOkHttpUtil.builder()
            .url(UsgsInfo.USGS_M2M_GET_TOKEN)
            .addParam("username", UsgsInfo.USGS_USER_NAME)
            .addParam("token", UsgsInfo.USGS_M2M_API_TOKEN)
            .post(true)
            .sync();
        JSONObject jsonObject = JSON.parseObject(response);
        return jsonObject.getString("data");
    }

    private static String search(String token,
                                 int maxResults,
                                 int startingNumber,
                                 String datasetName,
                                 JSONObject geoJson,
                                 String startDate,
                                 String endDate,
                                 Integer cloudMin,
                                 Integer cloudMax
                                 ) {
        JSONObject sceneFilter=new JSONObject();

        if(geoJson!=null) {
            JSONObject spatialFilter = new JSONObject();
            spatialFilter.put("filterType", "geojson");
            spatialFilter.put("geoJson", geoJson);
//            System.out.println(geoJson);
//            System.out.println(spatialFilter);
            sceneFilter.put("spatialFilter", spatialFilter);
        }
        if(!startDate.isEmpty()&&!endDate.isEmpty()){
            JSONObject acquisitionFilter=new JSONObject();
            acquisitionFilter.put("start",startDate);
            acquisitionFilter.put("end",endDate);
            sceneFilter.put("acquisitionFilter",acquisitionFilter);
        }
        if(ObjectUtils.isNotNull(cloudMin)&&ObjectUtils.isNotNull(cloudMax)){
            JSONObject cloudCoverFilter = new JSONObject();
            cloudCoverFilter.put("min", cloudMin);      // 最小云量
            cloudCoverFilter.put("max", cloudMax);     // 最大云量（0-100，自己改）
            sceneFilter.put("cloudCoverFilter", cloudCoverFilter);
        }

//        System.out.println(sceneFilter);

        String body = UsgsOkHttpUtil.builder()
            .url(UsgsInfo.USGS_M2M_SCENE_SEARCH)
            .addHeader("X-Auth-Token", token)
            .addParam("maxResults", maxResults)
            .addParam("startingNumber", startingNumber)
            .addParam("datasetName", datasetName)
            .addParam("sceneFilter", sceneFilter)
            .post(true)
            .sync();
        return processBody( body);
    }

    private static String processBody(String body){
        JSONObject parse = (JSONObject)JSONObject.parse(body);
        parse.remove("requestId");
        parse.remove("sessionId");
        parse.remove("version");
        JSONObject data = parse.getJSONObject("data");
        data.remove("totalHitsAccuracy");
        data.remove("isCustomized");
        data.remove("nextRecord");
        data.remove("numExcluded");
        JSONArray results = data.getJSONArray("results");
        for (Object result : results) {
            JSONObject resultObject = (JSONObject)result;
            resultObject.remove("metadata");
            resultObject.remove("options");
            resultObject.remove("selected");
        }
        return data.toString();
    }

    public static String search(
        int pageSize,
        int pageNum,
        String datasetName,
        String geoJsonStr,
        String startDate,
        String endDate,
        Integer cloudMin,
        Integer cloudMax
    ) {
        String token = getToken();
        JSONObject geoJson = null;
        if(!geoJsonStr.isEmpty()){
            geoJson=(JSONObject)JSON.parse(geoJsonStr);
        }
        return search(
            token,
            pageSize,
            ((pageNum-1)*pageSize)+1,
            datasetName,
            geoJson,
            startDate,
            endDate,
            cloudMin,
            cloudMax
        );
    }

    public static Map<String,String> periodSearch(
        String datasetName,
        String geoJsonStr,
        String startDate,
        String endDate,
        Integer cloudMin,
        Integer cloudMax
    ){
        int pageSize=10;
        int pageNum=1;
        Map<String,String> returnValue=new HashMap<>();
        String token = getToken();
        JSONObject geoJson = null;
        if(!geoJsonStr.isEmpty()){
            geoJson=(JSONObject)JSON.parse(geoJsonStr);
        }
        String searchValue = search(
            token,
            pageSize,
             1,
            datasetName,
            geoJson,
            startDate,
            endDate,
            cloudMin,
            cloudMax
        );
        JSONObject parse = (JSONObject)JSONObject.parse(searchValue);
        Integer totalHits = parse.getInteger("totalHits");
        addReturnMap(returnValue,searchValue);
        int maxNum = totalHits / pageSize;
        for(;pageNum<=maxNum;pageNum++){
            String searchValue1 = search(
                token,
                pageSize,
                ((pageNum - 1) * pageSize) + 1,
                datasetName,
                geoJson,
                startDate,
                endDate,
                cloudMin,
                cloudMax
            );
            addReturnMap(returnValue,searchValue1);
        }


        return returnValue;
    }

    private static void addReturnMap(Map<String,String> returnValue,String searchValue){
        JSONObject parse = (JSONObject)JSONObject.parse(searchValue);
        JSONArray results = parse.getJSONArray("results");
        for (Object result : results) {
            JSONObject resultObject = (JSONObject)result;
            returnValue.put(resultObject.getString("displayId"),
            resultObject.getString("entityId"));
        }
    }

    public static void main(String[] args) {
//        System.out.println(periodSearch(
//             UsgsInfo.LANDSAT89_DATASAT_NAME,
//            "{\"type\":\"Polygon\",\"coordinates\":[[[108.71834208435773,23.861868768851952],[108.71834208435773,20.401553955292897],[110.81928158373232,20.401553955292897],[110.81928158373232,23.861868768851952],[108.71834208435773,23.861868768851952]]]}",
//            "2025-08-18T06:55:56.723Z", "2025-10-17T06:55:56.723Z"
//        ));
    }
}
