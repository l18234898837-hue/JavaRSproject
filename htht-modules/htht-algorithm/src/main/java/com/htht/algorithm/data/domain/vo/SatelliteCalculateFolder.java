package com.htht.algorithm.data.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/12 16:21
 **/
@Data
public class SatelliteCalculateFolder {
    String name;
    List<SatelliteCalculateData> data;
}
