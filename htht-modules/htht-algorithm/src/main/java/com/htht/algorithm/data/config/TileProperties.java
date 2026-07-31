package com.htht.algorithm.data.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 切片配置类
 *
 * @auth wjx
 * @date 2023-4-21
 */
@Data
@Component
@ConfigurationProperties(prefix = "tile")
public class TileProperties {

    private String basePath;
    private String scanPath;
    private String algorithmPath;
    private String onlineDate;
    private String SatelliteCalculatePath;

    private Sld sld;
    /**
     * 栅格配置
     */
    private Grid grid;

    /**
     * 矢量配置
     */
    private Vector vector;

    /**
     * 贴图配置
     */
    private Image image;

    /**
     * 入库文件配置
     */
    private Scan scan;

    @Data
    @NoArgsConstructor
    public static class Grid {
        /**
         * 同时缓存多少影像信息
         */
        private int maxSize;
        /**
         * 是否开启缓存
         */
        private Boolean cacheable = Boolean.TRUE;

        /**
         * 切片存储文件夹路径
         */
        private String tileDir;
        /**
         * tif全波段切片存储文件夹路径
         */
        private String tifDir;
        private String uploadPath;
        private Integer minZoom;
        private Integer maxZoom;
        private String thumbPath;
    }

    @Data
    @NoArgsConstructor
    public static class Vector {
        /**
         * 切片文件夹路径
         */
        private String tileDir;
        /**
         * 解压文件夹路径
         */
        private String unzipDir;

        /**
         *  入库文件解压路径
         */
        private String storeDir;

        /**
         * 在线数据下载路径
         */
        private String onlineDir;

        /**
         * 下载文件压缩路径(返回前端的暂存)
         */
        private String downZipDir;

        /**
         * 最小层级
         */
        private int minZoom;
        /**
         * 最大层级
         */
        private int maxZoom;

        /**
         * cygwin路径
         */
        private String cygwinPath;
        /**
         * tippecanoe路径
         */
        private String tippecanoePath;
        private String uploadPath;
    }

    @Data
    @NoArgsConstructor
    public static class Image {
        /**
         * 同时缓存多少影像信息
         */
        private int maxSize;
        /**
         * 是否开启缓存
         */
        private Boolean cacheable = Boolean.TRUE;

        /**
         * 切片存储文件夹路径
         */
        private String tileDir;
        private String uploadPath;
    }

    @Data
    @NoArgsConstructor
    public static class Sld {
        private String uploadPath;
    }

    @Data
    @NoArgsConstructor
    public static class Scan{
        private String StoreDir;
        private String LandsatDir;
        private String sentinel2Dir;
        private String worldviewDir;
    }
}
