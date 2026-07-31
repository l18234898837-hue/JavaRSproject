package com.htht.algorithm.data.utils.scanStore.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StDataType;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.StAlgorithmRecordCatalogInfoService;
import com.htht.algorithm.data.service.StDataTypeService;
import com.htht.algorithm.data.utils.scanStore.BaseScanStore;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.exception.GlobalException;

import java.io.File;
import java.util.Map;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/5 16:03
 **/
public class GaoFenScanStore  extends BaseScanStore {

    private final static String GAOFEN_SCAN_STORE_TYPE = "GaoFen_SCAN_STORE";

    public GaoFenScanStore(TileProperties tileProperties, IBusCatalogInfoService catalogInfoService, IBusDataInfoService dataInfoService, StDataTypeService stDataTypeService, IBusFileInfoService fileInfoService, StAlgorithmRecordCatalogInfoService stAlgorithmRecordCatalogInfoService) {
        super(tileProperties, catalogInfoService, dataInfoService, stDataTypeService, fileInfoService, stAlgorithmRecordCatalogInfoService);
    }


    @Override
    public StDataType getDataTypeByStr(String str, int ModeType) {
        return null;
    }

    @Override
    public BusDataInfo transformTypeFolderStr(String FolderStr, int ModeType,boolean isScanZip) {
        return null;
    }

    @Override
    public BusDataInfo transformTypeFileStr(String FileStr, int ModeType) {
        return null;
    }

    @Override
    public void storeL0() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L0");
    }

    @Override
    public void storeL1() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L1");
    }

    @Override
    public void storeL2A0() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L2A0");
    }

    @Override
    public void storeL2A1() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L2A1");
    }

    @Override
    public void storeL2A2() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L2A2");
    }

    @Override
    public void storeL2B() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L2B");
    }
    @Override
    public void storeL3A0() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L3A0");
    }

    @Override
    public void storeL3A1() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L3A1");
    }

    @Override
    public void storeL3A2() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L3A2");
    }


    @Override
    public void storeL3B1() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L3B1");
    }

    @Override
    public void storeL3B2() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L3B2");
    }

    @Override
    public void storeL4A1() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L4A1");
    }

    @Override
    public void storeL4A2() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L4A2");
    }

    @Override
    public void storeL4B1() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L4B1");
    }

    @Override
    public void storeL4B2() {
        UnImplError(GAOFEN_SCAN_STORE_TYPE+"_L4B2");
    }

    @Override
    public void storeOlineDate(String zipFilePath, String dataTypeStr, int modeType) {
        throw new GlobalException("未实现该子类入库，联系管理员");
    }


    @Override
    public Map<String,String> getStoreTif(File ZipFile, String unZip) {
        throw new GlobalException("未实现，联系管理员");
    }


}
