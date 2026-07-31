package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.dto.TimeSeqPointDto;
import com.htht.algorithm.data.domain.dto.TimeSeqRectDto;
import com.htht.algorithm.data.domain.vo.TimeSeqVo;
import com.htht.algorithm.data.enums.FileType;
import com.htht.algorithm.data.enums.L2NcFileType;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.TimeSeqService;
import com.htht.algorithm.data.tile.grid.utils.GridUtil;
import lombok.RequiredArgsConstructor;
import me.msfy.domain.DataBound;
import me.msfy.domain.PointData;
import me.msfy.utils.GeoUtils;
import me.msfy.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/8 15:02
 **/

@Service
@RequiredArgsConstructor
public class TimeSeqServiceImpl implements TimeSeqService {
    private final IBusDataInfoService dataInfoService;

    @Override
    public List<TimeSeqVo> timeSeqPoint(TimeSeqPointDto timeSeqPointDto) throws Exception {
        List<TimeSeqVo> returnValue=new ArrayList<>();
        List<Long> ids = timeSeqPointDto.getIds();
        for (Long id : ids) {
            BusDataInfo dataInfo = dataInfoService.getById(id);
            FileType fileType = FileType.getByType(dataInfo.getType());
            double tempDouble=Double.NaN;
            if(fileType==FileType.GEO_TIFF){
                double[] valueByLonLat = GeoUtils.getValueByLonLat(
                    dataInfo.getTransformPath(),
                    timeSeqPointDto.getLon(),
                    timeSeqPointDto.getLat(),
                    new DataBound().setLonMin(dataInfo.getLngMin())
                        .setLonMax(dataInfo.getLngMax())
                        .setLatMin(dataInfo.getLatMin())
                        .setLatMax(dataInfo.getLatMax())
                );
                //如果波段超过1就不处理
                if(valueByLonLat.length==1){
                    tempDouble=valueByLonLat[0];
                }
            }else if(fileType==FileType.NC){
                L2NcFileType ncFileType = L2NcFileType.isNcFileType(dataInfo.getName());
                if(ncFileType==null){
                    double[] valueByLonLat = GeoUtils.getValueByLonLat(
                        dataInfo.getTransformPath(),
                        timeSeqPointDto.getLon(),
                        timeSeqPointDto.getLat(),
                        new DataBound().setLonMin(dataInfo.getLngMin())
                            .setLonMax(dataInfo.getLngMax())
                            .setLatMin(dataInfo.getLatMin())
                            .setLatMax(dataInfo.getLatMax())
                    );
                    //如果波段超过1就不处理
                    if(valueByLonLat.length==1){
                        tempDouble=valueByLonLat[0];
                    }
                }else{
                    List<PointData> pointData = JsonUtils.readPointsFromJson(dataInfo.getTransformPath());
                    tempDouble= GeoUtils.getValueByLonLat(
                        pointData,
                        timeSeqPointDto.getLon(),
                        timeSeqPointDto.getLat(),
                        new DataBound().setLonMin(dataInfo.getLngMin())
                            .setLonMax(dataInfo.getLngMax())
                            .setLatMin(dataInfo.getLatMin())
                            .setLatMax(dataInfo.getLatMax())
                    );
                }
            }
            returnValue.add(new TimeSeqVo().setId(id).setProductTime(dataInfo.getProductTime()).setValue(tempDouble).setName(dataInfo.getName()));
        }
        return returnValue;
    }

    @Override
    public List<TimeSeqVo> timeSeqRect(TimeSeqRectDto timeSeqRectDto) throws Exception {
        List<TimeSeqVo> returnValue=new ArrayList<>();
        List<Long> ids = timeSeqRectDto.getIds();
        DataBound dataBound = new DataBound()
            .setLatMax(timeSeqRectDto.getMaxLat())
            .setLatMin(timeSeqRectDto.getMinLat())
            .setLonMax(timeSeqRectDto.getMaxLon())
            .setLonMin(timeSeqRectDto.getMinLon());
        for (Long id : ids) {
            BusDataInfo dataInfo = dataInfoService.getById(id);
            FileType fileType = FileType.getByType(dataInfo.getType());
            double tempDouble=Double.NaN;
            if(fileType==FileType.GEO_TIFF){
                double[] valueByLonLat = GeoUtils.getValueByBound(
                    dataInfo.getTransformPath(),
                    dataBound,
                    new DataBound().setLonMin(dataInfo.getLngMin())
                        .setLonMax(dataInfo.getLngMax())
                        .setLatMin(dataInfo.getLatMin())
                        .setLatMax(dataInfo.getLatMax())
                );
                //如果波段超过1就不处理
                if(valueByLonLat.length==1){
                    tempDouble=valueByLonLat[0];
                }
            }else if(fileType==FileType.NC){
                L2NcFileType ncFileType = L2NcFileType.isNcFileType(dataInfo.getName());
                if(ncFileType==null){
                    double[] valueByLonLat = GeoUtils.getValueByBound(
                        dataInfo.getTransformPath(),
                        dataBound,
                        new DataBound()
                            .setLonMin(dataInfo.getLngMin())
                            .setLonMax(dataInfo.getLngMax())
                            .setLatMin(dataInfo.getLatMin())
                            .setLatMax(dataInfo.getLatMax())
                    );
                    //如果波段超过1就不处理
                    if(valueByLonLat.length==1){
                        tempDouble=valueByLonLat[0];
                    }
                }else{
                    List<PointData> pointData = JsonUtils.readPointsFromJson(dataInfo.getTransformPath());
                    tempDouble= GeoUtils.getValueByBound(
                        pointData,
                        dataBound,
                        new DataBound()
                            .setLonMin(dataInfo.getLngMin())
                            .setLonMax(dataInfo.getLngMax())
                            .setLatMin(dataInfo.getLatMin())
                            .setLatMax(dataInfo.getLatMax())
                    );
                }
            }
            returnValue.add(new TimeSeqVo().setId(id).setProductTime(dataInfo.getProductTime()).setValue(tempDouble).setName(dataInfo.getName()));
        }
        return returnValue;
    }
}
