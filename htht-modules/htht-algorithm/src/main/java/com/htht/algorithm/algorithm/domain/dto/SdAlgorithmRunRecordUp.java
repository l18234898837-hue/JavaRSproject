package com.htht.algorithm.algorithm.domain.dto;

import lombok.Data;
import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.NotNull;

/**
* 算法运行记录表 更新请求 DTO
*
* @author MSFY
* @since 2026-07-22
*/
@Data
@Validated
public class SdAlgorithmRunRecordUp implements Serializable {

    private static final long serialVersionUID = 1L;

        /**
        * 自增主键
        */
            @NotNull(message = "ID 不能为空")
        private Long id;

        /**
        * 算法id（关联sd_algorithm_file.id）
        */
        private Long algorithmId;

        /**
        * 算法运行开始时间
        */
        private LocalDateTime startTime;

        /**
        * 算法结束时间
        */
        private LocalDateTime endTime;

        /**
        * 算法运行状态
        */
        private String runStatus;

        /**
        * 算法日志
        */
        private String log;

        /**
        * 运行总时间（单位：毫秒）
        */
        private Long runDuration;

}
