package com.htcloud.orbit.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * @program: product-server
 * @description:
 * @author: wzq
 * @date: 2025-06-12
 **/
@Data
@AllArgsConstructor
public class StepHandler {

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date time;
    /**
     * 在区域内的足迹 [[经度,纬度，高度(米)],...]
     */
    private List<Double> footprints;

}
