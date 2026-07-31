package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("st_Voyages")
public class StVoyages extends BaseEntity {
    //航次id
    @TableId
    private Long Id;

    //航次编号
    @NotBlank(message="航次编号不能为空")
    private String voyageCode;

    //航次名称
    @NotBlank(message="航次名称不能为空")
    private String voyageName;

    //航次日期
    private LocalDate voyageDate;

    //航次人员
    private String crewMembers;

    //船名称
    private String vesselInfo;

    //仪器信息
    private String instrumentInfo;

    //数据概括
    private String dataDescription;

    //具体种类
    private String dataTypes;

    //站位分布
    private String stationDistribution;

    //图片id集合
    private String imageIds;
}
