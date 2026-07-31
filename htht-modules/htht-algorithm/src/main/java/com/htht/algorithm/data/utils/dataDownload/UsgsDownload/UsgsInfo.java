package com.htht.algorithm.data.utils.dataDownload.UsgsDownload;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/8/29 16:34
 **/
public interface UsgsInfo {
    // 网络爬虫用的数据
     String USGS_USER_NAME="sidachui";
     String USGS_PASSWORD="a123456789A.";
     String LOGIN_PAGE_URL = "https://earthexplorer.usgs.gov/login";
     String LOGIN_API_URL = "https://earthexplorer.usgs.gov/login";
     String SAVE_URL = "https://earthexplorer.usgs.gov/tabs/save";
     String SEARCH_URL = "https://earthexplorer.usgs.gov/scene/search";


     // USGS M2M API 相关数据
     String USGS_M2M_API_TOKEN="4wFtuI@Ry43nhcp6RHuuT7Xxf_Uqkn8wzLte!B8XBod1oY91z6iYngRLwGDSdOpI";
     String USGS_M2M_GET_TOKEN="https://m2m.cr.usgs.gov/api/api/json/stable/login-token";
     String USGS_M2M_SCENE_SEARCH="https://m2m.cr.usgs.gov/api/api/json/stable/scene-search";

     String LANDSAT45_DATASAT_NAME="landsat_tm_c2_l1";
     String LANDSAT45_DATASAT_ID="5e83d0a05ee25348";
     String LANDSAT7_DATASAT_NAME="landsat_etm_c2_l1";
     String LANDSAT7_DATASAT_ID="5e83d0d02b146ae0";
     String LANDSAT89_DATASAT_NAME="landsat_ot_c2_l1";
     String LANDSAT89_DATASAT_ID="5e81f14f59432a27";

     public static String getUsgsId(String datasatName){
         switch (datasatName){
             case "landsat_tm_c2_l1":
                 return "5e83d0a05ee25348";
             case "landsat_etm_c2_l1":
                 return "5e83d0d02b146ae0";
             case "landsat_ot_c2_l1":
                 return "5e81f14f59432a27";
             default:
                 return "";
         }
     }

}
