package com.htht.algorithm.data.constants;

public interface DataConstants {


    /**
     * 资源数据正常状态
     */
    String DATA_NORMAL = "0";

    /**
     * 资源数据封禁状态
     */
    String DATA_DISABLE = "1";

    /**
     * 资源数据存在状态
     */
    String DATA_EXIST = "0";
    /**
     * 资源数据删除状态
     */
    String DATA_DELETE = "2";

    /**
     * 资源数据未入库
     */
    String DATA_UN_STOCK = "0";

    /**
     * 资源数据已入库
     */
    String DATA_STOCK_SUCCESS = "1";
    /**
     * 资源数据入库失败
     */
    String DATA_STOCK_ERROR = "3";

    /**
     * 资源数据入库中
     */
    String DATA_STOCK_ING = "2";

    /**
     * 栅格资源缓存名称
     */
    String DATA_GRID_CACHE_NAME = "data_grid";

    /**
     * 普通目录
     */
    Integer CATALOG_COMMON_TYPE = 0;

    /**
     * 资源目录
     */
    Integer CATALOG_DATA_TYPE = 1;

    /**
     * 根目录父级ID
     */
    Long CATALOG_ROOT_ID = 0L;

    /**
     * 栅格
     */
    Integer DATA_TYPE_GRID = 1;

    /**
     * 矢量
     */
    Integer DATA_TYPE_VECTOR = 2;

    /**
     * 栅格压缩包
     */
    Integer Data_type_GRIDZIP = 5;
}
