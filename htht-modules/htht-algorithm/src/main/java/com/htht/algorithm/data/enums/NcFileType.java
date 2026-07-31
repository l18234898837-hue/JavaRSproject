package com.htht.algorithm.data.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.htht.common.exception.GlobalException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;

import static com.htht.algorithm.data.utils.ncOrHdf.gdal.NcToGeoTiffConverter.autoFileRun;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/3/4 16:36
 **/
@Getter
@AllArgsConstructor
public enum NcFileType {
    SLA("SLA"),
    SSHU("SSHU"),
    SST("SST"),
    SWH("SWH"),
    SSW("SSW"),
    ZSD("ZSD");

    @EnumValue
    private  final String Type;

    public static NcFileType isNcFileType(String type) {
        for (NcFileType ncFileType : NcFileType.values()) {
            if (ncFileType.getType().equals(type)) {
                return ncFileType;
            }
        }
        return null;
    }

    public static String NCFileTypeAutoHandle(NcFileType type,String ncFilePath,String dataInfoName){
        return switch (type) {
            case SLA, SSHU, SST, ZSD -> autoFileRun(ncFilePath, null).get(0);
            case SWH -> autoFileRun(ncFilePath, "Grid/SWH_g").get(0);
            case SSW -> SSWHandle(ncFilePath,dataInfoName);
            default -> throw new GlobalException("未知的NC数据类型：" + type);
        };
    }

    private static String SSWHandle(String ncFilePath,String dataInfoName){
        if(dataInfoName.contains("nearshorespd")){
           return autoFileRun(ncFilePath, "nearshorespd").get(0);
        }else if(dataInfoName.contains("nearshoredir")){
           return autoFileRun(ncFilePath, "nearshoredir").get(0);

        }
       return autoFileRun(ncFilePath, null).get(0);
    }

}
