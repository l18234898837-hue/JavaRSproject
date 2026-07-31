package com.htht.algorithm.data.domain.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/30 17:27
 **/
@Data
@Accessors(chain = true)
public class StSubmitAuditRecordVo {

    private Long id;

    /**
     * 提交用户id
     */
    private String userName;

    private Date submitTime;

    /**
     * 提交数据数量
     */
    private Integer submitCount;

    /**
     * 申请理由
     */
    private String auditReason;

    /**
     * 申请状态（1:待审批，2:已审批，3:已通过，4：已驳回）
     */
    private Short auditStatus;


    private List<StSubmitAuditRecordDataVo> dataVoList;
}
