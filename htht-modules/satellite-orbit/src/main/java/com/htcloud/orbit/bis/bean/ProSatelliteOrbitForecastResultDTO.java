package com.htcloud.orbit.bis.bean;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class ProSatelliteOrbitForecastResultDTO {
    /**
     * 过境卫星有效覆盖率总计
     */
    private Double coverageRate;

    /**
     * 预测结果列表
     */
    private List<ProSatelliteOrbitForecastResultItemDTO> forecastList = new ArrayList<>();
}
