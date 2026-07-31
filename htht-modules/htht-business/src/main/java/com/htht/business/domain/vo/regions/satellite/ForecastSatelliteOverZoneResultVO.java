package com.htht.business.domain.vo.regions.satellite;

import com.htcloud.orbit.model.ZoneDetectResult;
import lombok.Data;

import java.util.List;

@Data
public class ForecastSatelliteOverZoneResultVO {

    /**
     * 卫星轨道预测结果
     */
    private List<ZoneDetectResult> zoneDetectResult;

    /**
     * 卫星ID
     */
    private Long satelliteInfoId;

    /**
     * 卫星名称
     */
    private String satelliteName;

    /**
     * NORAD编号
     */
    private String noradNumber;
}
