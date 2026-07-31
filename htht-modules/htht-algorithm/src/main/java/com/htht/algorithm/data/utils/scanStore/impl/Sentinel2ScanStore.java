package com.htht.algorithm.data.utils.scanStore.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StDataType;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.StAlgorithmRecordCatalogInfoService;
import com.htht.algorithm.data.service.StDataTypeService;
import com.htht.algorithm.data.utils.DateUtil;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.algorithm.data.utils.GdalUtil;
import com.htht.algorithm.data.utils.scanStore.BaseScanStore;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.exception.GlobalException;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/5 11:02
 **/
@Service
public class Sentinel2ScanStore extends BaseScanStore {
    private final static String  SENTINEL2_SCAN_STORE_TYPE="SENTINEL2_SCAN_STORE";
    private final String Sentinel2Dir = tileProperties.getScan().getSentinel2Dir();

    public Sentinel2ScanStore(TileProperties tileProperties, IBusCatalogInfoService catalogInfoService, IBusDataInfoService dataInfoService, StDataTypeService stDataTypeService, IBusFileInfoService fileInfoService, StAlgorithmRecordCatalogInfoService stAlgorithmRecordCatalogInfoService) {
        super(tileProperties, catalogInfoService, dataInfoService, stDataTypeService, fileInfoService, stAlgorithmRecordCatalogInfoService);
    }


    private String getSentinel2Str(String str){
        return switch (str){
            case "S2A"->"Sentinel-2A";
            case "S2B"->"Sentinel-2B";
            case "S2C"->"Sentinel-2C";
            default -> null;
        };
    }

    @Override
    public StDataType getDataTypeByStr(String str, int ModeType) {
        String[] s = str.split("_");
        String sentinel2Str = getSentinel2Str(s[0]);
        if(sentinel2Str==null){
            return  null;
        }
        return switch (ModeType){
            case L1_MODE -> stDataTypeService.getResourceCatalogId(getSentinel2Str(s[0]), "L1");
            case L2_A0_MODE -> stDataTypeService.getResourceCatalogId(getSentinel2Str(s[0]), "L2A0");
            case L2_A2_MODE -> stDataTypeService.getResourceCatalogId(getSentinel2Str(s[0]), "L2A2");
            case L3_A0_MODE -> stDataTypeService.getResourceCatalogId(getSentinel2Str(s[0]), "L3A0");
            case L3_A1_MODE -> stDataTypeService.getResourceCatalogId(getSentinel2Str(s[0]), "L3A1");
            case L3_A2_MODE -> stDataTypeService.getResourceCatalogId(getSentinel2Str(s[0]), "L3A2");
            default -> null;
        };
    }

    @Override
    public BusDataInfo transformTypeFolderStr(String FolderStr, int ModeType,boolean isScanZip) {
        String[] s=FolderStr.split("_");
        String ActualStr = s[2].split("T")[0];
        String ProductStr= s[6].split("T")[0];
        BusDataInfo busDataInfo=getStoreDefault(isScanZip);
        switch (ModeType){
            case L1_MODE,L2_A0_MODE:
                busDataInfo.setActualTime(DateUtil.getFormatterDate(ActualStr,"yyyyMMdd"));
                busDataInfo.setProductTime(DateUtil.getFormatterDate(ProductStr,"yyyyMMdd"));
                return busDataInfo;
        }
        return null;
    }

    @Override
    public BusDataInfo transformTypeFileStr(String FileStr, int ModeType) {
        String[] s=FileStr.split("_");
        BusDataInfo busDataInfo=getStoreDefault(false);
        switch(ModeType){
            case L2_A2_MODE,L3_A0_MODE,L3_A1_MODE,L3_A2_MODE:
                Date date = DateUtil.getFormatterDate(s[2] + s[3] + s[4], "yyyyMMdd");
                busDataInfo.setProductTime(date);
                busDataInfo.setActualTime(date);
                return busDataInfo;
        }
        return null;
    }

    @Override
    public void storeL0() {
//        unZipL0ToL1(new File(Sentinel2Dir));
        storeL0Zip(new File(Sentinel2Dir));
    }


    @Override
    public void storeL1() {
        File file = FileStrUtil.searchChildOneFolder(new File(Sentinel2Dir), "L1");
        if(file==null){
            return;
        }
        scanSentinelL1(file,L1_MODE);
    }

    private void scanSentinelL1(File file,int ModeType) {
        if(file==null){
            return;
        }
        File[] files = file.listFiles();//获取L1文件夹下的所有文件
        if(files==null){
            return;
        }
        for (File file1 : files) {
            StDataType dataType = getDataTypeByStr(file1.getName(), ModeType);
            if(dataType==null){
                continue;
            }
            BusDataInfo busDataInfo = transformTypeFolderStr(file1.getName(), ModeType,false);
            if(busDataInfo==null){
                continue;
            }
            File GranuleFile = FileStrUtil.searchFolder(file1,"GRANULE");
            File ImgDataFile = FileStrUtil.searchFolder(GranuleFile,"IMG_DATA");
            File[] JpFiles = ImgDataFile.listFiles();
            if(JpFiles==null){
                continue;
            }
            for (File jpFile : JpFiles) {
                if(isJP2(jpFile)){
                    String toTifUrl = GdalUtil.Jp2ToGeoTiff(jpFile);
                    BusDataInfo clone = busDataInfo.clone();
                    scanGrid(new File(toTifUrl),clone);
                    catalogInfoService.bindDataToCatalog(clone.getId(),dataType.getProductCatalogId());
                }
            }
            FileStrUtil.deleteFolderRecursively(file1);
        }

    }

    @Override
    public void storeL2A0() {
        store(Sentinel2Dir,"L2","L2A0",L2_A0_MODE,false,true);
    }

    @Override
    public void storeL2A1() {
        UnImplError(SENTINEL2_SCAN_STORE_TYPE+"_L2A1");
    }

    @Override
    public void storeL2A2() {
        store(Sentinel2Dir,"L2","L2A2",L2_A2_MODE,true,true);
    }

    @Override
    public void storeL2B() {
        UnImplError(SENTINEL2_SCAN_STORE_TYPE+"_L2B");
    }
    @Override
    public void storeL3A0() {
        store(Sentinel2Dir,"L3","L3A0",L3_A0_MODE,true,true);
    }

    @Override
    public void storeL3A1() {
        store(Sentinel2Dir,"L3","L3A1",L3_A1_MODE,true,true);
    }

    @Override
    public void storeL3A2() {
        store(Sentinel2Dir,"L3","L3A2",L3_A2_MODE,true,true);
    }


    @Override
    public void storeL3B1() {
        UnImplError(SENTINEL2_SCAN_STORE_TYPE+"_L3B1");
    }

    @Override
    public void storeL3B2() {
        UnImplError(SENTINEL2_SCAN_STORE_TYPE+"_L3B2");
    }

    @Override
    public void storeL4A1() {
        UnImplError(SENTINEL2_SCAN_STORE_TYPE+"_L4A1");
    }

    @Override
    public void storeL4A2() {
        UnImplError(SENTINEL2_SCAN_STORE_TYPE+"_L4A2");
    }

    @Override
    public void storeL4B1() {
        UnImplError(SENTINEL2_SCAN_STORE_TYPE+"_L4B1");
    }

    @Override
    public void storeL4B2() {
        UnImplError(SENTINEL2_SCAN_STORE_TYPE+"_L4B2");
    }

    @Override
    public void storeOlineDate(String zipFilePath, String dataTypeStr, int modeType) {
        File file = FileStrUtil.unZip(new File(zipFilePath), dataTypeStr, false);
        switch (modeType){
            case L1_MODE:
                storeGridZip(new File(zipFilePath));
                break;
            case L2_A0_MODE:
                scanNextFolderAllTif(file, modeType);
                break;
            case L2_A2_MODE,L3_A0_MODE,L3_A1_MODE,L3_A2_MODE:
                scanNextFolderIsland(file, modeType);
                break;
            default: throw new GlobalException("未实现该子类入库，联系管理员");
        }
    }


    @Override
    public Map<String,String> getStoreTif(File ZipFile, String unZip) {
        FileStrUtil.unZip(ZipFile,unZip,false);
        Map<String,String> map=new HashMap<>();
        File unZipFolder = new File(unZip);
        File[] folderNextS = unZipFolder.listFiles();
        if (folderNextS==null) {
            throw new GlobalException("解压失败");
        }
        File[] folderNextTowS = folderNextS[0].listFiles();
        if (folderNextTowS==null) {
            throw new GlobalException("解压失败");
        }
//        File[] folderNextThreeS = folderNextTowS[0].listFiles();
//        if(folderNextThreeS==null){
//            throw new GlobalException("解压失败");
//        }
        for (File folderNextThree : folderNextTowS) {
            if(isImage(folderNextThree)){
                map.put("thumbPath",folderNextThree.getAbsolutePath());
                break;
            }
        }


        File GranuleFile = FileStrUtil.searchFolder(unZipFolder,"GRANULE");
        File ImgDataFile = FileStrUtil.searchFolder(GranuleFile,"IMG_DATA");
        File[] JpFiles = ImgDataFile.listFiles();
        if(JpFiles==null){
            throw new GlobalException("未找到文件");
        }
        for (File jpFile : JpFiles) {
            if(isJP2(jpFile)){
                map.put("tifPath",jpFile.getAbsolutePath());
                break;
            }
        }
        for (File file : JpFiles){
            boolean isBreak=false;
            if(file.isDirectory()){
                File[] files1 = file.listFiles();
                if (files1 != null) {
                    for (File file1 : files1) {
                        if(isJP2(file1)){
                            map.put("tifPath",file1.getAbsolutePath());
                            isBreak=true;
                            break;
                        }
                    }
                }
            }
            if(isBreak){
                break;
            }
        }
        if(map.size()==2){
            return map;
        }else{
            throw new GlobalException("没找到相关tif文件或者缩略图");
        }
    }

    public void storeOnlineDownloadData(String filePath,String name){
        File file = new File(filePath);
        File[] files = file.listFiles();
        if(files==null){
            return;
        }
        BusDataInfo dataInfo =transformTypeFolderStr(name,L1_MODE,false);
        StDataType dataType = getDataTypeByStr(name, L1_MODE);
        for (File jpFile : files) {
            if(isTif(jpFile)){
                String toTifUrl = GdalUtil.Jp2ToGeoTiff(jpFile);
                BusDataInfo clone = dataInfo.clone();
                scanGridTakePrefix(new File(toTifUrl),clone,name+"_"+FileStrUtil.getNameWithoutExtension(jpFile));
                catalogInfoService.bindDataToCatalog(clone.getId(),dataType.getProductCatalogId());
            }
        }
    }


    public static void main(String[] args) {

    }
}
