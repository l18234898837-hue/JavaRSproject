package com.htcloud.orbit.bis.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ProSatelliteOrbitForecastStateDto {

    private Double lon; // 经度

    private Double lat; // 纬度

    private Double altitude; // 高度

    private String geomJson;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date forecastStamp; // 时刻

}
