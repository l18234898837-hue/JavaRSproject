package com.htht.algorithm.data.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/8 15:06
 **/
@Data
@Accessors(chain = true)
public class TimeSeqPointDto {
    List<Long> ids;
    Double lon;
    Double lat;
}
