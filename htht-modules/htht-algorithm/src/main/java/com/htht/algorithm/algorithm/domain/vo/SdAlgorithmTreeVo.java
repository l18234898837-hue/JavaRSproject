package com.htht.algorithm.algorithm.domain.vo;

import com.htht.algorithm.algorithm.domain.SdAlgorithmFile;
import com.htht.algorithm.algorithm.domain.SdAlgorithmLibrary;
import lombok.Data;

import java.util.List;

/**
 * Author: MSFY
 * Description:
 * DateTime: 2026/7/21 09:38
 **/
@Data
public class SdAlgorithmTreeVo {
    private SdAlgorithmLibraryVo   sdAlgorithmLibrary;
    private List<SdAlgorithmFileVo>  sdAlgorithmFiles;
}
