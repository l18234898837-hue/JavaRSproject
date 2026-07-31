package com.htht.algorithm.data.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.htht.algorithm.data.domain.vo.ColorRampVo;
import com.htht.common.exception.GlobalException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.msfy.ProcessDataByJson;
import me.msfy.ProcessDataToPng;
import me.msfy.domain.DataBound;
import me.msfy.domain.DataVariable;

import java.util.List;

/**
* Author: MSFY
* Description: TODO
* DateTime: 2026/4/14 10:01
**/

@Getter
@AllArgsConstructor
public enum L2NcFileType {

    FY3E("FY3E"),
    FY3G("FY3G"),
    H1C("H1C"),
    H1D("H1D"),
    H1E("H1E"),
    H2B("H2B"),
    H2C("H2C"),
    H2D("H2D"),
    FY3E_GNOSR("GNOSR"),
    FY3E_WRAD("WRAD");

    @EnumValue
    private final String Type;

    public static L2NcFileType isNcFileType(String ncName){
        String[] split = ncName.split("_");
        for(L2NcFileType ncFileType:L2NcFileType.values()){
            if(ncFileType.getType().equals(split[0])){
                if(ncFileType.equals(L2NcFileType.FY3E)){
                    if(split[1].contains("GNOSR")){
                        return  L2NcFileType.FY3E_GNOSR;
                    } else if (split[1].contains("WRAD")) {
                        return L2NcFileType.FY3E_WRAD;
                    }
                }
                return ncFileType;
            }
        }
        return null;
    }

    public static void createJson(String filePath, String jsonPath, L2NcFileType l2NcFileType) throws Exception {
        if(l2NcFileType.equals(L2NcFileType.FY3E_GNOSR)||l2NcFileType.equals(L2NcFileType.FY3G)){
            List<DataVariable> dataVariables = List.of(
                new DataVariable("BDS/WindSpeedProduct/Sws_lon", "BDS/WindSpeedProduct/Sws_lat", "BDS/WindSpeedProduct/Sws"),
                new DataVariable("GAL/WindSpeedProduct/Sws_lon", "GAL/WindSpeedProduct/Sws_lat", "GAL/WindSpeedProduct/Sws"),
                new DataVariable("GPS/WindSpeedProduct/Sws_lon", "GPS/WindSpeedProduct/Sws_lat", "GPS/WindSpeedProduct/Sws")
            );
            ProcessDataByJson.process1DToJson(filePath, dataVariables,jsonPath );
        }else if(l2NcFileType.equals(L2NcFileType.FY3E_WRAD)){
            List<DataVariable> dataVariables=List.of(
                new DataVariable("Ku_band/wvc_lon","Ku_band/wvc_lat","Ku_band/wind_speed_selected")
            );
            ProcessDataByJson.process2DToJson(filePath, dataVariables,jsonPath );
        }else if(l2NcFileType.equals(L2NcFileType.H1C)||l2NcFileType.equals(L2NcFileType.H1D)||l2NcFileType.equals(L2NcFileType.H1E)){
            List<DataVariable> dataVariables=List.of(
                new DataVariable("Navigation_Data/Longitude","Navigation_Data/Latitude","Geophysical_Data/CDOM")
            );
            ProcessDataByJson.process2DToJson(filePath, dataVariables,jsonPath );
        } else if (l2NcFileType.equals(L2NcFileType.H2B)||l2NcFileType.equals(L2NcFileType.H2C)||l2NcFileType.equals(L2NcFileType.H2D)) {
            List<DataVariable> dataVariables = List.of(
                new DataVariable("lon", "lat", "swh_c")
            );
            ProcessDataByJson.process1DToJson(filePath, dataVariables,jsonPath );
        }

        throw new GlobalException("没有该L2级别处理方法，检查文件");
    }
    public static DataBound createThumbByJson(String jsonFilePath, String thumbPath, L2NcFileType l2NcFileType,ColorRampVo colorRamp) throws Exception {
        if(l2NcFileType.equals(L2NcFileType.FY3E_GNOSR)||l2NcFileType.equals(L2NcFileType.FY3G)){
            float scale = 40;
            return ProcessDataByJson.jsonToPng(jsonFilePath, colorRamp.getMinValue(), colorRamp.getMaxValue(), colorRamp.getColorRamp(),scale,thumbPath);
        }else if(l2NcFileType.equals(L2NcFileType.FY3E_WRAD)){
            float scale = 30;
            return ProcessDataByJson.jsonToPng(jsonFilePath,colorRamp.getMinValue(),colorRamp.getMaxValue(),colorRamp.getColorRamp(),scale,thumbPath);
        }else if(l2NcFileType.equals(L2NcFileType.H1C)||l2NcFileType.equals(L2NcFileType.H1D)||l2NcFileType.equals(L2NcFileType.H1E)){
            float scale = 100;
            return ProcessDataByJson.jsonToPng(jsonFilePath,colorRamp.getMinValue(),colorRamp.getMaxValue(),colorRamp.getColorRamp(),scale,thumbPath);
        } else if (l2NcFileType.equals(L2NcFileType.H2B)||l2NcFileType.equals(L2NcFileType.H2C)||l2NcFileType.equals(L2NcFileType.H2D)) {
            float scale = 40;
            return ProcessDataByJson.jsonToPng(jsonFilePath, colorRamp.getMinValue(), colorRamp.getMaxValue(), colorRamp.getColorRamp(),scale,thumbPath);
        }
        throw new GlobalException("没有该L2级别处理方法，检查文件");
    }

    public static DataBound createThumb(String filePath, String thumbPath,L2NcFileType l2NcFileType, ColorRampVo colorRamp) throws Exception {
        if(l2NcFileType.equals(L2NcFileType.FY3E_GNOSR)||l2NcFileType.equals(L2NcFileType.FY3G)){
            List<DataVariable> dataVariables = List.of(
                new DataVariable("BDS/WindSpeedProduct/Sws_lon", "BDS/WindSpeedProduct/Sws_lat", "BDS/WindSpeedProduct/Sws"),
                new DataVariable("GAL/WindSpeedProduct/Sws_lon", "GAL/WindSpeedProduct/Sws_lat", "GAL/WindSpeedProduct/Sws"),
                new DataVariable("GPS/WindSpeedProduct/Sws_lon", "GPS/WindSpeedProduct/Sws_lat", "GPS/WindSpeedProduct/Sws")
            );
            float scale = 40;
            return ProcessDataToPng.Process1DToPng(filePath, dataVariables, colorRamp.getMinValue(), colorRamp.getMaxValue(), colorRamp.getColorRamp(),scale,thumbPath);
        }else if(l2NcFileType.equals(L2NcFileType.FY3E_WRAD)){
            List<DataVariable> dataVariables=List.of(
                new DataVariable("Ku_band/wvc_lon","Ku_band/wvc_lat","Ku_band/wind_speed_selected")
            );
            float scale = 30;
            return ProcessDataToPng.Process2DToPng(filePath,dataVariables,colorRamp.getMinValue(),colorRamp.getMaxValue(),colorRamp.getColorRamp(),scale,thumbPath);
        }else if(l2NcFileType.equals(L2NcFileType.H1C)||l2NcFileType.equals(L2NcFileType.H1D)||l2NcFileType.equals(L2NcFileType.H1E)){
            List<DataVariable> dataVariables=List.of(
                new DataVariable("Navigation_Data/Longitude","Navigation_Data/Latitude","Geophysical_Data/CDOM")
            );
            float scale = 100;
            return ProcessDataToPng.Process2DToPng(filePath,dataVariables,colorRamp.getMinValue(),colorRamp.getMaxValue(),colorRamp.getColorRamp(),scale,thumbPath);
        } else if (l2NcFileType.equals(L2NcFileType.H2B)||l2NcFileType.equals(L2NcFileType.H2C)||l2NcFileType.equals(L2NcFileType.H2D)) {
            List<DataVariable> dataVariables = List.of(
                    new DataVariable("lon", "lat", "swh_c")
            );
            float scale = 40;
            return ProcessDataToPng.Process1DToPng(filePath, dataVariables, colorRamp.getMinValue(), colorRamp.getMaxValue(), colorRamp.getColorRamp(),scale,thumbPath);
        }


        throw new GlobalException("没有该L2级别处理方法，检查文件");
    }
}
