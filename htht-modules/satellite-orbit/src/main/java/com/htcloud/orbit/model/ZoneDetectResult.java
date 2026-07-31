package com.htcloud.orbit.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class ZoneDetectResult implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = -6190689122701100762L;

    /**
     * 进入区域的时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date entryTime;
    /**
     * 离开区域的时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date exitTime;
    /**
     * 在区域内的足迹 [[经度,纬度，高度(米)],...]
     */
    private List<List<Double>> footprints = new ArrayList<>();

    private ZoneDetectResult beforeEnter;
    private ZoneDetectResult afterExit;


    @Override
    public ZoneDetectResult clone() {
        try {
            return (ZoneDetectResult) super.clone();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
