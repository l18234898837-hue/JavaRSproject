package com.htcloud.orbit.bis.bean;

import com.htcloud.orbit.model.ZoneDetectResult;
import lombok.Data;

import java.util.List;

@Data
public class SatelliteForecastDto {

    private List<ZoneDetectResult> results;
}
