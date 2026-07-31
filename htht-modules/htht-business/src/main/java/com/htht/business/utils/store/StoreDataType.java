package com.htht.business.utils.store;


/**
 * Author: MSFY
 * Description: TODO 弃用，后面增加了目录，导致id跟名称过于多了，无法通过文件管理，相关方法弃用
 * DateTime: 2025/7/24 16:29
 **/
public interface StoreDataType {
    int TEMP_L1_TYPE=1;
    int LANDSAT_5_TYPE=2;
    int LANDSAT_7_TYPE=3;
    int LANDSAT_8_TYPE=4;
    int LANDSAT_9_TYPE=5;
    int SENTINEL_2A_TYPE=6;
    int SENTINEL_2B_TYPE=7;
    int WORLDVIEW_2_TYPE=8;
    int GAOFEN_2_TYPE=9;

    String TEMP_L1_MENU_STR="Temp-L1";
    String LANDSAT_5_MENU_STR="Landsat-5";
    String LANDSAT_7_MENU_STR="Landsat-7";
    String LANDSAT_8_MENU_STR="Landsat-8";
    String LANDSAT_9_MENU_STR="Landsat-9";
    String SENTINEL_2A_MENU_STR="Sentinel-2A";
    String SENTINEL_2B_MENU_STR="Sentinel-2B";
    String WORLDVIEW_2_MENU_STR="WorldView-2";
    String GAOFEN_2_MENU_STR="GaoFen-2";

    static String getMenuStr(int type) {
        switch ( type){
            case TEMP_L1_TYPE:
                return TEMP_L1_MENU_STR;
            case LANDSAT_5_TYPE:
                return LANDSAT_5_MENU_STR;
            case LANDSAT_7_TYPE:
                return LANDSAT_7_MENU_STR;
            case LANDSAT_8_TYPE:
                return LANDSAT_8_MENU_STR;
            case LANDSAT_9_TYPE:
                return LANDSAT_9_MENU_STR;
            case SENTINEL_2A_TYPE:
                return SENTINEL_2A_MENU_STR;
            case SENTINEL_2B_TYPE:
                return SENTINEL_2B_MENU_STR;
            case WORLDVIEW_2_TYPE:
                return WORLDVIEW_2_MENU_STR;
            case GAOFEN_2_TYPE:
                return GAOFEN_2_MENU_STR;
            default:
                return "";
        }
    }


}
