package com.htht.algorithm.data.domain.vo;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/1 14:09
 **/
@Data
@Validated
public class UsgsSreachVo {
    @NotNull(message = "输入每页个数")
    int pageSize;
    @NotNull(message = "输入页码")
    int pageNum;
    @NotBlank(message = "输入数据名称")
    String datasetName;
    String geoJsonStr;
    String startDate;
    String endDate;
    Integer onlineType;
    String wtk;
    List<String> sentinel2Types;
    Integer cloudMin;
    Integer cloudMax;
}
