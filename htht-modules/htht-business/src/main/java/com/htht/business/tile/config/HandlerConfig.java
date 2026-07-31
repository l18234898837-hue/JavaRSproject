package com.htht.business.tile.config;

import com.basic.gdal.image.conversion.*;
import com.basic.gdal.image.model.ImageInfo;
import com.basic.gdal.image.model.WebTileInfo;
import com.basic.resultcreator.image.color.ColorCreator;
import com.basic.resultcreator.image.color.ColorOptions;
import com.basic.resultcreator.image.color.NormalColorCreator;
//import com.basic.tile.server.TileHandler;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.service.IBusDataInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class HandlerConfig {


//    @Bean
//    public TileHandler tileHandler(){
//        return new TileHandler() {
//            @Override
//            protected String onCreateTile(String path) {
//                return null;
//            }
//
//            @Override
//            protected AlgorithmTileConverter getRasterConverter(WebTileInfo tileInfo) {
//                return new AlgorithmTileConverter(){
//                    @Override
//                    public RasterWriter<float[][]> createWriter(ImageInfo imageInfo, String destPath) {
//                        return new ImageIOWriter(tileInfo){
//                            @Override
//                            public ColorCreator getColorCreator() {
//                                ColorOptions options = new ColorOptions(new float[]{4000, 5500});
//                                return new NormalColorCreator(options);
//                            }
//                        };
//                    }
//                };
//            }
//
//            @Override
//            protected String getPath(WebTileInfo tileInfo) {
//                System.out.println(tileInfo);
//                BusDataInfo dataInfo = busDataInfoService.getDataInfoById(Long.parseLong(tileInfo.getFileId()));
//                System.out.println(dataInfo);
//                return "D:\\server\\scan\\L8_OLI_2013_05_21_02_55_44_122048_rhos_1-10-84.tif";
//            }
//        };
//    }
//
//    @Bean
//    public TileHandler tileHandler1(){
//        return new TileHandler() {
//            @Override
//            protected String onCreateTile(String path) {
//                return null;
//            }
//            @Override
//            protected AbstractRasterConverter<?> getRasterConverter(WebTileInfo tileInfo) {
//                return new PngRasterConverter();
//            }
//            @Override
//            protected String getPath(WebTileInfo tileInfo) {
//                return "D:\\server\\scan\\L8_OLI_2013_05_21_02_55_44_122048_rhos_1-10-84.tif";
//            }
//            @Override
//            public String getRequestMapping() {
//                return "/tile1/{zoom}/{row}/{col}";
//            }
//        };
//    }
//
//    @Bean
//    public TileHandler tileHandler2(){
//        return new TileHandler() {
//            @Override
//            protected String onCreateTile(String path) {
//                return null;
//            }
//            @Override
//            protected AlgorithmTileConverter getRasterConverter(WebTileInfo tileInfo) {
//                return new AlgorithmTileConverter(){
//                    @Override
//                    public RasterWriter<float[][]> createWriter(ImageInfo imageInfo, String destPath) {
//                        return new ImageIOWriter(tileInfo);
//                    }
//                };
//            }
//            @Override
//            protected String getPath(WebTileInfo tileInfo) {
//                return "null";
//            }
//
//            @Override
//            public String getRequestMapping() {
//                return "/tile2/{zoom}/{row}/{col}";
//            }
//        };
//    }
}
