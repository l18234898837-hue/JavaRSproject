package com.htht.algorithm.data.domain.vo;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/26 16:27
 **/
@Validated
@Data
public class BusDataInfoDownloadVo {
    //返回压缩包名称
    @NotBlank(message = "压缩包名称不能为空")
    private String name;
    //需要下载的资源id数组
    @NotEmpty(message = "需要下载的资源id数组不能为空")
    private List<Long> dataIds;

    private  int type;
}
