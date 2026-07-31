//package com.htht.business.tile.controller;
//
//import com.basic.gdal.image.conversion.*;
//import com.basic.gdal.image.model.ImageInfo;
//import com.basic.gdal.image.model.WebTileInfo;
//import com.basic.resultcreator.image.color.ColorCreator;
//import com.basic.resultcreator.image.color.ColorOptions;
//import com.basic.resultcreator.image.color.NormalColorCreator;
////import com.basic.tile.server.TileHandler;
//import com.basic.tile.server.starter.componet.HttpServerRunner;
//import com.htht.algorithm.data.domain.BusDataInfo;
//import com.htht.algorithm.data.service.IBusDataInfoService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Controller;
//
//import javax.annotation.PostConstruct;
//import javax.annotation.Resource;
//
///**
// * Author: MSFY
// * Description: TODO
// * DateTime: 2025/7/17 16:32
// **/
//@Controller
//@RequiredArgsConstructor
//public class tileController {
//    private final HttpServerRunner httpServerRunner;
//    private final IBusDataInfoService busDataInfoService;
//
//    @PostConstruct
//    public void init() {
//        System.out.println(httpServerRunner);
//        System.out.println("bean后初始化控制器");
//
//        httpServerRunner.registerHandler(new TileHandler() {
//            @Override
//            protected String getPath(WebTileInfo tileInfo) {
//                BusDataInfo dataInfo = busDataInfoService.getDataInfoById(Long.parseLong(tileInfo.getFileId()));
//                return dataInfo.getTransformPath();
//            }
//
//            @Override
//            protected String onCreateTile(String path) {
//                return null;
//            }
//
//            @Override
//            protected AbstractRasterConverter<?> getRasterConverter(WebTileInfo tileInfo) {
//                return new PngRasterConverter();
//            }
//        });
//
//        httpServerRunner.registerHandler(new TileHandler() {
//            @Override
//            protected String getPath(WebTileInfo tileInfo) {
//                BusDataInfo dataInfo = busDataInfoService.getDataInfoById(Long.parseLong(tileInfo.getFileId()));
//                return dataInfo.getTransformPath();
//            }
//
//            @Override
//            protected String onCreateTile(String path) {
//                return null;
//            }
//
//            @Override
//            protected AbstractRasterConverter<?> getRasterConverter(WebTileInfo tileInfo) {
//                return new AlgorithmTileConverter(){
//                    @Override
//                    public RasterWriter<float[][]> createWriter(ImageInfo imageInfo, String destPath) {
//                        return new ImageIOWriter(tileInfo){
//                            @Override
//                            public ColorCreator getColorCreator() {
//                                ColorOptions options = new ColorOptions(new float[]{4000, 4500});
//                                options.setColorScale(new String[]{"#2966a3", "#358ca9", "#4A9194", "#4D8D7C", "#4CA44C", "#66A335", "#A1873F", "#A16C5B", "#8C3F5B", "#974B90"});
//                                return new NormalColorCreator(options);
//                            }
//                        };
//                    }
//                };
//            }
//
//            @Override
//            public String getRequestMapping() {
//                return "/tile2/{zoom}/{row}/{col}";
//            }
//        });
//
//        httpServerRunner.registerHandler(new TileHandler() {
//            @Override
//            protected String getPath(WebTileInfo tileInfo) {
//                String path="D:\\file\\浅海地形遥感反演平台\\5、参考数据\\数据入库_测试数据\\GF2\\GF2_PMS1_E114.0_N22.6_20221012_L1A0006817151\\GF2_PMS1_E114.0_N22.6_20221012_L1A0006817151-MSS1.tiff";
//                return path;
//            }
//
//            @Override
//            protected String onCreateTile(String path) {
//                return null;
//            }
//
//            @Override
//            protected AbstractRasterConverter<?> getRasterConverter(WebTileInfo tileInfo) {
//                return new PngRasterConverter();
//            }
//
//            @Override
//            public String getRequestMapping() {
//                return "/tile3/{zoom}/{row}/{col}";
//            }
//        });
//    }
//
//
//}
