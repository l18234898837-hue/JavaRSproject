package com.htht.algorithm.data.utils.scanStore.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.constants.DataConstants;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StDataType;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.StAlgorithmRecordCatalogInfoService;
import com.htht.algorithm.data.service.StDataTypeService;
import com.htht.algorithm.data.utils.DateUtil;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.algorithm.data.utils.scanStore.BaseScanStore;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.exception.GlobalException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/5 11:02
 **/
@Service
public class LandsatScanStore extends BaseScanStore {
    private final static String LANDSAT_SCAN_STORE_TYPE = "LANDSAT_SCAN_STORE";
    private final String landsatDir = tileProperties.getScan().getLandsatDir();

    public LandsatScanStore(TileProperties tileProperties, IBusCatalogInfoService catalogInfoService, IBusDataInfoService dataInfoService, StDataTypeService stDataTypeService, IBusFileInfoService fileInfoService, StAlgorithmRecordCatalogInfoService stAlgorithmRecordCatalogInfoService) {
        super(tileProperties, catalogInfoService, dataInfoService, stDataTypeService, fileInfoService, stAlgorithmRecordCatalogInfoService);
    }


    private List<File> searchLandsatDir(String childDir) {
        List<File> returnFiles= new ArrayList<>();
        File file = new File(landsatDir);
        File[] files = file.listFiles();
        if (files == null) {
            return returnFiles;
        }
        for (File file1 : files) {//获取区号文件夹
            File[] files1 = file1.listFiles();
            if (files1 == null) {
                continue;
            }
            for (File file2 : files1) {//获取L1文件夹
                if(file2.getName().equals(childDir)&&file2.isDirectory()){
                    returnFiles.add(file2);
                    }
            }
        }
        return returnFiles;
    }

    private List<File> searchLandsatChildDir(String childDir,String childDirAndChild){
        List<File> returnFiles= new ArrayList<>();
        List<File> fileList = searchLandsatDir(childDir);
        if(!fileList.isEmpty()){
            for (File file : fileList) {
                File file1 = FileStrUtil.searchChildOneFolder(file, childDirAndChild);
                if(file1!=null){
                    returnFiles.add(file1);
                }
            }
        }
        return returnFiles;
    }

    private String getLandsatStr(String  str){
        return switch (str) {
            case "LT05", "L5" -> "Landsat-5";
            case "LE07", "L7" -> "Landsat-7";
            case "LC08", "L8" -> "Landsat-8";
            case "LC09", "L9" -> "Landsat-9";
            default -> null;
        };
    }

    /**
     * 可以通过文件夹入库的通用方法
     * @param fileList
     * @param ModeType
     */
    private void scanFolder(List<File> fileList, int ModeType) {
        if(fileList.isEmpty()){
            return;
        }
        for (File file1 : fileList) {
            scanNextFolderAllTif(file1, ModeType);
        }
    }

    /**
     * 通过文件入库的通用方法
     * @param fileList
     * @param modeType
     */
    private void scanFile(List<File> fileList, int modeType) {
        for (File file : fileList) {
            scanNextFolderIsland(file, modeType);
        }
    }



    @Override
    public StDataType getDataTypeByStr(String str, int ModeType) {
        String[] s = str.split("_");
        String landsatStr = getLandsatStr(s[0]);
        if(landsatStr==null){
            return  null;
        }
        return switch (ModeType) {
            case L1_MODE -> stDataTypeService.getResourceCatalogId(getLandsatStr(s[0]), "L1");
            case L2_A0_MODE -> stDataTypeService.getResourceCatalogId(getLandsatStr(s[0]), "L2A0");
            case L2_A2_MODE -> stDataTypeService.getResourceCatalogId(getLandsatStr(s[0]), "L2A2");
            case L3_A0_MODE -> stDataTypeService.getResourceCatalogId(getLandsatStr(s[0]), "L3A0");
            case L3_A1_MODE -> stDataTypeService.getResourceCatalogId(getLandsatStr(s[0]), "L3A1");
            case L3_A2_MODE -> stDataTypeService.getResourceCatalogId(getLandsatStr(s[0]), "L3A2");
            default -> null;
        };
    }

    @Override
    public BusDataInfo transformTypeFolderStr(String FolderStr, int ModeType,boolean isScanZip) {
        String[] s = FolderStr.split("_");
        BusDataInfo busDataInfo=null;
        busDataInfo = getStoreDefault(isScanZip);
        switch (ModeType){
            case L1_MODE,L2_A0_MODE:
                busDataInfo.setActualTime(DateUtil.getFormatterDate(s[3],"yyyyMMdd"));
                busDataInfo.setProductTime(DateUtil.getFormatterDate(s[4],"yyyyMMdd"));
                return busDataInfo;
        }
        return null;
    }

    @Override
    public BusDataInfo transformTypeFileStr(String FileStr, int ModeType) {
        String[] s = FileStr.split("_");
        BusDataInfo busDataInfo = getStoreDefault(false);
        switch (ModeType){
            case L2_A2_MODE,L3_A0_MODE,L3_A1_MODE,L3_A2_MODE :
                Date date = DateUtil.getFormatterDate(s[2] + s[3] + s[4], "yyyyMMdd");
                busDataInfo.setActualTime(date);
                busDataInfo.setProductTime(date);
                return busDataInfo;
        }
        return null;
    }


    @Override
    public void storeL0() {
        File file = new File(landsatDir);
        File[] files = file.listFiles();
        if (files == null) {
            return;
        }
        for (File file1 : files) {
            //解压缩处理，不做该处理，进行压缩包入库
//           unZipL0ToL1(file1);
            storeL0Zip(file1);
        }
    }



    @Override
    public void storeL1() {
        List<File> fileList = searchLandsatDir("L1");
        scanFolder(fileList, L1_MODE);
    }


    @Override
    public void storeL2A0() {
        List<File> fileList = searchLandsatChildDir("L2","L2A0");
        scanFolder(fileList, L2_A0_MODE);
    }

    @Override
    public void storeL2A1() {
        UnImplError(LANDSAT_SCAN_STORE_TYPE+"_L2A1");
    }

    @Override
    public void storeL2A2() {
        List<File> fileList = searchLandsatChildDir("L2","L2A2");

        scanFile(fileList, L2_A2_MODE);

    }

    @Override
    public void storeL2B() {
        UnImplError(LANDSAT_SCAN_STORE_TYPE+"_L2B");
    }
    @Override
    public void storeL3A0() {
        List<File> fileList = searchLandsatChildDir("L3","L3A0");
        scanFile(fileList, L3_A0_MODE);
    }



    @Override
    public void storeL3A1() {
        List<File> fileList = searchLandsatChildDir("L3","L3A1");
        scanFile(fileList, L3_A0_MODE);
    }

    @Override
    public void storeL3A2() {
        List<File> fileList = searchLandsatChildDir("L3","L3A2");
        scanFile(fileList, L3_A0_MODE);
    }

    @Override
    public void storeL3B1() {
        UnImplError(LANDSAT_SCAN_STORE_TYPE+"_L3B1");
    }

    @Override
    public void storeL3B2() {
        UnImplError(LANDSAT_SCAN_STORE_TYPE+"_L3B2");
    }

    @Override
    public void storeL4A1() {
        UnImplError(LANDSAT_SCAN_STORE_TYPE+"_L4A1");
    }

    @Override
    public void storeL4A2() {
        UnImplError(LANDSAT_SCAN_STORE_TYPE+"_L4A2");
    }

    @Override
    public void storeL4B1() {
        UnImplError(LANDSAT_SCAN_STORE_TYPE+"_L4B1");
    }

    @Override
    public void storeL4B2() {
        UnImplError(LANDSAT_SCAN_STORE_TYPE+"_L4B2");
    }

    @Override
    public void storeOlineDate(String zipFilePath, String dataTypeStr, int modeType) {
        File file = FileStrUtil.unZip(new File(zipFilePath), dataTypeStr, false);
        switch (modeType){
            case L1_MODE:
                storeGridZip(new File(zipFilePath));
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

        File unZipFolder = new File(unZip);
        File[] files = unZipFolder.listFiles();
        if (files == null) {
            throw new GlobalException("解压失败");
        }
        Map<String,String> map=new HashMap<>();
        for (File file : files) {
            boolean scanTif=false;
            boolean scanImage=false;
            if (file.isFile() && isTif( file)&&!scanTif) {
                map.put("tifPath",file.getAbsolutePath());
            }else if (file.isFile()&& isImage(file)&&!scanImage) {
                String nameWithoutExtension = FileStrUtil.getNameWithoutExtension(file);
                if (nameWithoutExtension.endsWith("_large")) {
                    map.put("thumbPath",file.getAbsolutePath());
                }
            }
        }
        if(map.size()==2){
            return map;
        }else{
            throw new GlobalException("没找到相关tif文件或者缩略图");
        }
    }


    public static void main(String[] args) {

    }
}
