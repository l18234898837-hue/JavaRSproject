package com.htht.algorithm.data.utils.scanStore;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.constants.DataConstants;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StDataType;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.StAlgorithmRecordCatalogInfoService;
import com.htht.algorithm.data.service.StDataTypeService;
import com.htht.algorithm.data.service.impl.BusDataInfoServiceImpl;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.utils.file.FileUtils;
import jodd.io.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/4 16:18
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public abstract class BaseScanStore {
    protected final TileProperties tileProperties;
    protected final IBusCatalogInfoService catalogInfoService;
    protected final IBusDataInfoService dataInfoService;
    protected final StDataTypeService stDataTypeService;
    protected final IBusFileInfoService fileInfoService;
    protected final StAlgorithmRecordCatalogInfoService stAlgorithmRecordCatalogInfoService;

    protected final int L1_MODE = 1;
    protected final int L2_A0_MODE = 2;
    protected final int L2_A1_MODE = 3;
    protected final int L2_A2_MODE = 4;
    protected final int L2_B_MODE = 5;
    protected final int L3_A0_MODE = 6;
    protected final int L3_A1_MODE = 7;
    protected final int L3_A2_MODE = 8;
    protected final int L3_B1_MODE = 9;
    protected final int L3_B2_MODE = 10;
    protected final int L4_A1_MODE = 11;
    protected final int L4_A2_MODE = 12;
    protected final int L4_B1_MODE = 13;
    protected final int L4_B2_MODE = 14;

    public abstract StDataType getDataTypeByStr(String str,int ModeType);
    public abstract BusDataInfo transformTypeFolderStr(String FolderStr,int ModeType,boolean isScanZip);
    public abstract BusDataInfo transformTypeFileStr(String FileStr,int ModeType);

    public abstract void storeL0();
    public abstract void storeL1();
    public abstract void storeL2A0();
    public abstract void storeL2A1();
    public abstract void storeL2A2();
    public abstract void storeL2B();
    public abstract void storeL3A0();
    public abstract void storeL3A1();
    public abstract void storeL3A2();
    public abstract void storeL3B1();

    public abstract void storeL3B2();
    public abstract void storeL4A1();
    public abstract void storeL4A2();
    public abstract void storeL4B1();
    public abstract void storeL4B2();

    public abstract void storeOlineDate(String zipFilePath,String dataTypeStr,int modeType) throws  Exception;

    public void storeAlgorithmRunFolder(String folder,Long recordId){
        store(folder,"L2","L2A0",L2_A0_MODE,false,false,true,recordId);
        store(folder,"L2","L2A2",L2_A2_MODE,true,false,true,recordId);
        store(folder,"L3","L3A0",L3_A0_MODE,true,false,true,recordId);
        store(folder,"L3","L3A1",L3_A1_MODE,true,false,true,recordId);
        store(folder,"L3","L3A2",L3_A2_MODE,true,false,true,recordId);
    }

    public abstract Map<String,String> getStoreTif(File ZipFile, String unZip);

    public BusDataInfo getStoreDefault(boolean isScanZip){
        BusDataInfo busDataInfo = new BusDataInfo();
        busDataInfo.setStock(DataConstants.DATA_UN_STOCK);
        busDataInfo.setStatus("1");
        if(isScanZip){
            busDataInfo.setType(DataConstants.Data_type_GRIDZIP);
        }else{
            busDataInfo.setType(DataConstants.DATA_TYPE_GRID);
        }
        return busDataInfo;
    }



    protected void UnImplError(String errorStr){
        log.error("ScanStore:{}没有实现,文件夹内没有提供该类型的数据样例", errorStr);
    }

    public void unZipL0ToL1(File UnFolder){
        File[] UnFolders = UnFolder.listFiles();
        if(UnFolders==null){
            return;
        }
        File tempL0=null;
        File tempL1=null;
        for (File LFolder : UnFolders) {
            if(LFolder.getName().equals("L0")&&LFolder.isDirectory()){
                tempL0=LFolder;
            }else if(LFolder.getName().equals("L1")&&LFolder.isDirectory()){
                tempL1=LFolder;
            }
        }
        if(tempL0==null||tempL1==null){
            return;
        }
        File[] unFiles= tempL0.listFiles();
        if(unFiles==null){
            return;
        }
        for (File unFile : unFiles) {
            String nameWithoutExtension = FileStrUtil.getNameWithoutExtension(unFile);
            FileStrUtil.unZip(unFile, Path.of(tempL1.getAbsolutePath(),nameWithoutExtension).toString(),true);
        }
    }

    public void storeL0Zip(File folder){
        File[] folders = folder.listFiles();
        if(folders==null){
            return;
        }
        for (File l0Folder : folders) {
            if(l0Folder.getName().equals("L0")&&l0Folder.isDirectory()){
                File[] files = l0Folder.listFiles();
                if(files==null){
                    return;
                }
                for (File file : files) {
                    storeGridZip(file);
                }
            }
        }
    }

    public void storeGridZip(File zipFile){
        String name = FileStrUtil.getNameWithoutExtension(zipFile);
        boolean b = canL0Scan(name);
        if(b){
            String nameWithoutExtension = FileStrUtil.getNameWithoutExtension(zipFile);
            StDataType dataTypeByStr = getDataTypeByStr(nameWithoutExtension, L1_MODE);
            BusDataInfo busDataInfo = transformTypeFolderStr(nameWithoutExtension, L1_MODE,true);
            if(busDataInfo!=null){
                scanGrid(zipFile, busDataInfo);
                catalogInfoService.bindDataToCatalog(busDataInfo.getId(),dataTypeByStr.getProductCatalogId());
            }

        }
    }

    public boolean canL0Scan(String name){
        StDataType dataTypeByStr = getDataTypeByStr(name, L1_MODE);
        return dataTypeByStr != null;
    }

    protected void scanGrid(File file,BusDataInfo busDataInfo){
        String prefix = FileUtils.getPrefix(file.getName());
        BusFileInfo fileInfo = fileInfoService.scan(file, tileProperties.getGrid().getUploadPath());
        busDataInfo.setFileId(fileInfo.getId());
        busDataInfo.setKeywords(prefix);
        busDataInfo.setName(prefix);
        busDataInfo.setRemark(prefix);
        dataInfoService.save(busDataInfo);
    }

    protected void scanGridTakePrefix(File file,BusDataInfo busDataInfo,String prefix){
        BusFileInfo fileInfo = fileInfoService.scan(file, tileProperties.getGrid().getUploadPath());
        busDataInfo.setFileId(fileInfo.getId());
        busDataInfo.setKeywords(prefix);
        busDataInfo.setName(prefix);
        busDataInfo.setRemark(prefix);
        dataInfoService.save(busDataInfo);
    }

    /**
     * 扫描下一级文件夹全是tif文件
     */
    protected void scanNextFolderAllTif(File file,int ModeType){
        scanNextFolderAllTif(file, ModeType,true,false,null);
    }

    protected void scanNextFolderAllTif(File file,int ModeType,boolean deleteFolder,boolean bindTempTab,Long recordId){
        if(file==null||file.isFile()){
            return;
        }
        File[] files = file.listFiles();
        if(files==null){
            return;
        }
        for (File file3 : files) {//获取每个数据文件夹
            boolean isScanOneFile=false;
            StDataType dataType = getDataTypeByStr(file3.getName(), ModeType);//从文件夹中获取数据类型用于绑定
            if(dataType==null){
                continue;
            }
            BusDataInfo busDataInfo = transformTypeFolderStr(file3.getName(), ModeType,false);//从文件夹中获取预制的BusDataInfo
            if(busDataInfo==null){
                continue;
            }
            File[] files3 = file3.listFiles();
            if (files3 == null) {
                continue;
            }
            for (File file4 : files3) {//获取其中的每个文件
                if(isTif(file4)){
                    BusDataInfo clone = busDataInfo.clone();
                    scanGrid(file4,clone);
                    isScanOneFile=true;
                    if(bindTempTab){
                        stAlgorithmRecordCatalogInfoService.bind(dataType.getId(),recordId,clone.getId());
                    }else{
                        catalogInfoService.bindDataToCatalog(clone.getId(),dataType.getProductCatalogId());
                    }
                }
            }
            if(isScanOneFile&&deleteFolder){
                FileStrUtil.deleteFolderRecursively(file3);
            }
        }
    }

    /**
     * 扫描下一级文件全是岛屿文件夹
     * @param file
     * @param ModeType
     */
    protected void scanNextFolderIsland(File file,int ModeType){
       scanNextFolderIsland(file, ModeType,true,false,null);
    }

    protected void scanNextFolderIsland(File file,int ModeType,boolean deleteFolder,boolean bindTempTab,Long recordId){
        if(file==null||file.isFile()){
            return;
        }
        File[] files = file.listFiles();
        if(files==null){
            return;
        }
        for (File file1 : files) {//获取每个数据文件夹
            boolean isScanOneFile=false;
            File[] files1 = file1.listFiles();
            if(files1==null){
                continue;
            }
            List<File> tifList=new ArrayList<>();
            List<File> imgList=new ArrayList<>();
            for (File file2 : files1) { //获取其中的每个文件
                if(isTif(file2)){
                    tifList.add(file2);
                }else if(isImage(file2)&&isCollectThumb(ModeType)){
                    imgList.add(file2);
                }
            }
            for (File tifFile : tifList) {
                BusDataInfo busDataInfo = transformTypeFileStr(tifFile.getName(), ModeType);
                if(busDataInfo==null){
                    continue;
                }
                StDataType dataType = getDataTypeByStr(tifFile.getName(), ModeType);
                if(dataType==null){
                    continue;
                }
                scanGrid(tifFile,busDataInfo);
                if(!imgList.isEmpty()){
                    recordDownloadThumb(imgList,tifFile,busDataInfo);
                }
                isScanOneFile=true;
                if(bindTempTab){
                    stAlgorithmRecordCatalogInfoService.bind(dataType.getId(),recordId,busDataInfo.getId());
                }else{
                    catalogInfoService.bindDataToCatalog(busDataInfo.getId(),dataType.getProductCatalogId());
                }
            }
            if(isScanOneFile&&deleteFolder){
                FileStrUtil.deleteFolderRecursively(file1);
            }
        }

    }

    protected void store(String storeDir,String LFolder,String LCFolder,int ModeType,boolean isLand,boolean deleteFolder){
        store(storeDir, LFolder, LCFolder, ModeType, isLand, deleteFolder,false, null);
    }
    protected void store(String storeDir,String LFolder,String LCFolder,int ModeType,boolean isLand,boolean deleteFolder,boolean bindTempTab,Long recordId){
        File LFile = FileStrUtil.searchChildOneFolder(new File(storeDir), LFolder);
        if(LFile==null){
            return;
        }
        File LCFolderFile = FileStrUtil.searchChildOneFolder(LFile, LCFolder);
        if(isLand){
            scanNextFolderIsland(LCFolderFile,ModeType,deleteFolder,bindTempTab,recordId);
        }else{
            scanNextFolderAllTif(LCFolderFile,ModeType,deleteFolder,bindTempTab,recordId);
        }
    }

    protected void recordDownloadThumb(List<File> imgList,File tifFile,BusDataInfo dataInfo)  {
        String name = FileStrUtil.getNameWithoutExtension(tifFile);
        for (File imgFile : imgList) {
            if (imgFile.getName().startsWith(name)) {
                try{
                    String downThumbPath = Path.of(tileProperties.getGrid().getThumbPath(), dataInfo.getId() + "-down.jpg").toString();
                    FileUtil.copy(imgFile.getAbsolutePath(), downThumbPath);
                    dataInfo.setDownloadThumbnail(downThumbPath);
                    dataInfoService.updateById(dataInfo);
                    return;
                }catch (Exception e){
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    protected boolean isCollectThumb(int ModeType){
        return switch (ModeType) {
            case L3_A1_MODE, L3_A2_MODE -> true;
            default -> false;
        };
    }

    protected boolean isTif(File file){
        return FileStrUtil.getExtension(file).equalsIgnoreCase("tif")||FileStrUtil.getExtension(file).equalsIgnoreCase("tiff");
    }

    protected boolean isImage(File file){
        return FileStrUtil.getExtension(file).equalsIgnoreCase("jpg")||
            FileStrUtil.getExtension(file).equalsIgnoreCase("png")||
            FileStrUtil.getExtension(file).equalsIgnoreCase("jpeg");
    }

    protected boolean isJP2(File file){
        return FileStrUtil.getExtension(file).equalsIgnoreCase("jp2");
    }

    protected boolean isXML(File file){
        return FileStrUtil.getExtension(file).equalsIgnoreCase("xml");
    }

    protected boolean isSuffix(File file,String suffix){
        return file.getName().toLowerCase().endsWith(suffix.toLowerCase());
    }

    public int getModeType(String str){
        return switch (str) {
            case "L1" -> L1_MODE;
            case "L2A0" -> L2_A0_MODE;
            case "L2A1" -> L2_A1_MODE;
            case "L2A2" -> L2_A2_MODE;
            case "L2B" -> L2_B_MODE;
            case "L3A0" -> L3_A0_MODE;
            case "L3A1" -> L3_A1_MODE;
            case "L3A2" -> L3_A2_MODE;
            case "L3B1" -> L3_B1_MODE;
            case "L3B2" -> L3_B2_MODE;
            case "L4A1" -> L4_A1_MODE;
            case "L4A2" -> L4_A2_MODE;
            case "L4B1" -> L4_B1_MODE;
            case "L4B2" -> L4_B2_MODE;
            default -> -1;
        };
    }
}
