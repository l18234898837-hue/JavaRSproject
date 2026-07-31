package com.htht.algorithm.data.domain.dto;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/25 10:46
 **/
@Data
@Validated
public class AddRunSST {
    @NotNull(message = "开始时间不能为空")
    private Date startDate;
    @NotNull(message = "结束时间不能为空")
    private Date endDate;

}
