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
* 算法库与算法文件关联表 更新请求 DTO
*
* @author MSFY
* @since 2026-07-21
*/
@Data
@Validated
public class SdAlgorithmLibraryRelationUp implements Serializable {

    private static final long serialVersionUID = 1L;

        /**
        * 自增主键
        */
            @NotNull(message = "ID 不能为空")
        private Long id;

        /**
        * 算法库id（关联sd_algorithm_library.id）
        */
        private Long libraryId;

        /**
        * 算法文件id（关联sd_algorithm_file.id）
        */
        private Long algorithmFileId;

}
