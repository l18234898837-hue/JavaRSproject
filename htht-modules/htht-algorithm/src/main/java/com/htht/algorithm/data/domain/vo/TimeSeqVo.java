package com.htht.algorithm.data.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/8 14:56
 **/
@Data
@Accessors(chain = true)
public class TimeSeqVo {
    private Long id;
    private String name;
    private Date productTime;
    private Double value;
}
