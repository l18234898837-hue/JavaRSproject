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
import com.htht.algorithm.data.utils.scanStore.BaseScanStore;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.exception.GlobalException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.Map;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/5 11:02
 **/
@Service
public class WorldviewScanStore extends BaseScanStore {
    private final static String WORLDVIEW_SCAN_STORE_TYPE="WORLDVIEW_SCAN_STORE";
    private final String WorldViewDir=tileProperties.getScan().getWorldviewDir();

    public WorldviewScanStore(TileProperties tileProperties, IBusCatalogInfoService catalogInfoService, IBusDataInfoService dataInfoService, StDataTypeService stDataTypeService, IBusFileInfoService fileInfoService, StAlgorithmRecordCatalogInfoService stAlgorithmRecordCatalogInfoService) {
        super(tileProperties, catalogInfoService, dataInfoService, stDataTypeService, fileInfoService, stAlgorithmRecordCatalogInfoService);
    }


    private String getWorldViewStr(String str){
        return switch (str){
          case "WorldView2","M2AS","P2AS"->"WorldView-2";
          case "WorldView3","M1BS","P1BS"->"WorldView-3";
          default -> null;
        };
    }

    @Override
    public StDataType getDataTypeByStr(String str, int ModeType) {
        String[] s=str.split("_");
        String worldViewStr=getWorldViewStr(s[0]);
        if(worldViewStr==null){
            return null;
        }
        return switch(ModeType){
            case L1_MODE -> stDataTypeService.getResourceCatalogId(getWorldViewStr(s[0]), "L1");
            case L2_A0_MODE -> stDataTypeService.getResourceCatalogId(getWorldViewStr(s[0]), "L2A0");
            case L2_A2_MODE -> stDataTypeService.getResourceCatalogId(getWorldViewStr(s[0]), "L2A2");
            case L3_A0_MODE -> stDataTypeService.getResourceCatalogId(getWorldViewStr(s[0]), "L3A0");
            case L3_A1_MODE -> stDataTypeService.getResourceCatalogId(getWorldViewStr(s[0]), "L3A1");
            case L3_A2_MODE -> stDataTypeService.getResourceCatalogId(getWorldViewStr(s[0]), "L3A2");
            default -> null;
        };
    }

    @Override
    public BusDataInfo transformTypeFolderStr(String FolderStr, int ModeType,boolean isScanZip) {
        BusDataInfo storeDefault = getStoreDefault(false);
        switch (ModeType){
            case L1_MODE:
                storeDefault.setProductTime(DateUtil.getFormatterDate(FolderStr,"yyyyMMdd"));
                storeDefault.setActualTime(DateUtil.getFormatterDate(FolderStr,"yyyyMMdd"));
                return storeDefault;
            default:
                return null;
        }
    }

    @Override
    public BusDataInfo transformTypeFileStr(String FileStr, int ModeType) {
        String[] s=FileStr.split("_");
        BusDataInfo storeDefault = getStoreDefault(false);
        switch (ModeType){
            case L2_A0_MODE,L3_A0_MODE,L3_A1_MODE:
                storeDefault.setProductTime(DateUtil.getFormatterDate(s[1] + s[2] + s[3],"yyyyMMdd"));
                storeDefault.setActualTime(DateUtil.getFormatterDate(s[1] + s[2] + s[3],"yyyyMMdd"));
                return storeDefault;
            default:
                return null;
        }
    }

    @Override
    public void storeL0() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L0");
    }

    @Override
    public void storeL1() {
        File L1File = FileStrUtil.searchChildOneFolder(new File(WorldViewDir), "L1");
        if(L1File==null){
            return;
        }
        scanWorldViewL1(L1File,L1_MODE);
    }

    private void scanWorldViewL1(File L1File,int ModeType){
        if(L1File==null){
            return;
        }
        File[] worldViewFolders = L1File.listFiles();
        if(worldViewFolders==null){
            return;
        }
        for (File worldViewFolder : worldViewFolders) {//进入到每个world文件文件夹中
            boolean isScanOneFile=false;
            File[] worldViewFiles = worldViewFolder.listFiles();
            if(worldViewFiles==null){
                continue;
            }
            File XMLFile=null;
            File MULFile=null;
            File PANFile=null;
            for (File worldViewFile : worldViewFiles) {
                if(isXML(worldViewFile)){
                    XMLFile=worldViewFile;
                }else if(isSuffix(worldViewFile,"_MUL")){
                    MULFile=worldViewFile;
                }else if(isSuffix(worldViewFile,"_PAN")) {
                    PANFile=worldViewFile;
                }
            }
            if(XMLFile==null){
                continue;
            }
            String xmlDate = getXMLDate(XMLFile);
            if(xmlDate==null){
                System.out.println("xml文件有问题，检查");
                continue;
            }
            BusDataInfo busDataInfo = transformTypeFolderStr(xmlDate, ModeType, false);

            if(MULFile!=null){
                File[] files2 = MULFile.listFiles();
                if(files2!=null){
                    for (File file2 : files2) {
                        if(isTif(file2)){
                            String worldViewTypeStr = file2.getName().split("-")[1];
                            StDataType dataTypeByStr = getDataTypeByStr(worldViewTypeStr, L1_MODE);
                            BusDataInfo clone = busDataInfo.clone();
                            scanGrid(file2,clone);
                            isScanOneFile=true;
                            catalogInfoService.bindDataToCatalog(clone.getId(),dataTypeByStr.getProductCatalogId());
                        }
                    }
                }
            }

            if(PANFile!=null){
                File[] files2 = PANFile.listFiles();
                if(files2!=null){
                    for (File file2 : files2) {
                        if(isTif(file2)){
                            String worldViewTypeStr = file2.getName().split("-")[1];
                            StDataType dataTypeByStr = getDataTypeByStr(worldViewTypeStr, L1_MODE);
                            BusDataInfo clone = busDataInfo.clone();
                            scanGrid(file2,clone);
                            isScanOneFile=true;
                            catalogInfoService.bindDataToCatalog(clone.getId(),dataTypeByStr.getProductCatalogId());
                        }
                    }
                }
            }
            if(isScanOneFile){
                FileStrUtil.deleteFolderRecursively(worldViewFolder);
            }
        }
    }

    @Override
    public void storeL2A0() {
        File L2File = FileStrUtil.searchChildOneFolder(new File(WorldViewDir), "L2");
        if(L2File==null){
            return;
        }
        File L2A0File = FileStrUtil.searchChildOneFolder(L2File, "L2A0");
        if(L2A0File==null){
            return;
        }
        scanNextFolderIsland(L2A0File,L2_A0_MODE);
    }

    @Override
    public void storeL2A1() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L2A1");
    }

    @Override
    public void storeL2A2() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L2A2");
    }

    @Override
    public void storeL2B() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L2B");
    }
    @Override
    public void storeL3A0() {
        File L3File = FileStrUtil.searchChildOneFolder(new File(WorldViewDir), "L3");
        if(L3File==null){
            return;
        }
        File L3A0File = FileStrUtil.searchChildOneFolder(L3File, "L3A0");
        if(L3A0File==null){
            return;
        }
        scanNextFolderIsland(L3A0File,L3_A0_MODE);
    }

    @Override
    public void storeL3A1() {
        File L3File = FileStrUtil.searchChildOneFolder(new File(WorldViewDir), "L3");
        if(L3File==null){
            return;
        }
        File L3A1File = FileStrUtil.searchChildOneFolder(L3File, "L3A1");
        if(L3A1File==null){
            return;
        }
        scanNextFolderIsland(L3A1File,L3_A1_MODE);
    }

    @Override
    public void storeL3A2() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L3A2");
    }


    @Override
    public void storeL3B1() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L3B1");
    }

    @Override
    public void storeL3B2() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L3B2");
    }

    @Override
    public void storeL4A1() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L4A1");
    }

    @Override
    public void storeL4A2() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L4A2");
    }

    @Override
    public void storeL4B1() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L4B1");
    }

    @Override
    public void storeL4B2() {
        UnImplError(WORLDVIEW_SCAN_STORE_TYPE+"_L4B2");
    }

    @Override
    public void storeOlineDate(String zipFilePath, String dataTypeStr, int modeType) {
        File file = FileStrUtil.unZip(new File(zipFilePath), dataTypeStr, false);
        switch (modeType){
            case L1_MODE:
                scanWorldViewL1(file,modeType);
                break;
             case L2_A0_MODE:
                 scanNextFolderIsland(file,modeType);
                break;
            case L3_A0_MODE,L3_A1_MODE:
                scanNextFolderIsland(file,L3_A1_MODE);
                break;
            default: throw new GlobalException("未实现该子类入库，联系管理员");
        }
    }

    @Override
    public Map<String,String> getStoreTif(File ZipFile, String unZip) {
        throw new GlobalException("未实现，联系管理员");
    }


    private static String getXMLDate(File file){
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);
            Element root = document.getDocumentElement();
            String mediacreationdate = root.getElementsByTagName("MEDIACREATIONDATE").item(0).getTextContent();
            String[] s = mediacreationdate.split("T")[0].split("-");
            String s1 = s[0] + s[1] + s[2];
            return s1;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
