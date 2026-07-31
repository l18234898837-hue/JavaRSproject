package com.htht.algorithm.algorithm.domain.dto;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
* 算法库与算法文件关联表 新增请求 DTO
*
* @author MSFY
* @since 2026-07-21
*/
@Data
@Validated
public class SdAlgorithmLibraryRelationDel implements Serializable {


        /**
        * 算法库id（关联sd_algorithm_library.id）
        */
        @NotNull(message = "算法库id不能为空")
        private Long libraryId;

        /**
        * 算法文件id（关联sd_algorithm_file.id）
        */
        @NotNull(message = "算法文件id不能为空")
        private Long algorithmFileId;

}
