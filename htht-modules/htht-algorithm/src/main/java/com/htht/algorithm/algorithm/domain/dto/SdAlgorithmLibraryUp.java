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
* 算法库表 更新请求 DTO
*
* @author MSFY
* @since 2026-07-24
*/
@Data
@Validated
public class SdAlgorithmLibraryUp implements Serializable {

    private static final long serialVersionUID = 1L;

        /**
        * 自增主键
        */
            @NotNull(message = "ID 不能为空")
        private Long id;

        /**
        * 库名称
        */
        private String libraryName;

        /**
        * 排序序号（数值越小越靠前）
        */
        private Integer sortOrder;

}
