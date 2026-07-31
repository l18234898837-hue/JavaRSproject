package com.htht.algorithm.data.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

    /**
  * Author: MSFY
  * Description: TODO
  * DateTime: 2025/11/19 14:55
  **/
@Data
@Accessors(chain = true)
public class SatelliteDto {
    private String satelliteName;
    private String satelliteLevel;
}
