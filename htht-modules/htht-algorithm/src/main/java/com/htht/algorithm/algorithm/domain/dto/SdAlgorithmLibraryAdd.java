package com.htht.algorithm.algorithm.domain.dto;

import lombok.Data;
import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import org.springframework.validation.annotation.Validated;

/**
* 算法库表 新增请求 DTO
*
* @author MSFY
* @since 2026-07-24
*/
@Data
@Validated
public class SdAlgorithmLibraryAdd implements Serializable {

    private static final long serialVersionUID = 1L;

        /**
        * 库名称
        */
        private String libraryName;

        /**
        * 排序序号（数值越小越靠前）
        */
        private Integer sortOrder;

}
