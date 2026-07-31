package com.htcloud.orbit.bis.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * @program: satellite-orbit
 * @description:
 * @author: wzq
 * @date: 2025-07-22
 **/
@Data
public class Param {

    private List<OrbitForecastSpecDto> orbitForecastSpecs;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    private String zone;

}
