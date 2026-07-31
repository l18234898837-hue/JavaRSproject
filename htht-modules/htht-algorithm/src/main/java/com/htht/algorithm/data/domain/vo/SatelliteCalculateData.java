package com.htht.algorithm.data.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/12 16:21
 **/
@Data
@Accessors(chain = true)
public class SatelliteCalculateData {
    String name;
    String thumbPath;
    String path;
}
