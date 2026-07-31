package com.htcloud.orbit.bis.bean;

import com.htcloud.orbit.model.TrackResult;
import lombok.Data;

import java.util.List;

@Data
public class SatelliteTrackDto {

    List<TrackResult> results;
}
