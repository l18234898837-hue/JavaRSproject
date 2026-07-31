package com.htht.algorithm.algorithm.service;

import com.htht.algorithm.algorithm.domain.SdAlgorithmLibrary;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryAdd;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryQuery;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryUp;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmLibraryVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
* 算法库表 服务类
*
* @author MSFY
* @since 2026-07-21
*/
public interface ISdAlgorithmLibraryService extends IService<SdAlgorithmLibrary> {

    boolean add(SdAlgorithmLibraryAdd addSdAlgorithmLibrary);

    boolean up(SdAlgorithmLibraryUp upSdAlgorithmLibrary);

    List<SdAlgorithmLibraryVo> getList(SdAlgorithmLibraryQuery query);

    SdAlgorithmLibraryVo getVoById(Long id);

    TableDataInfo<SdAlgorithmLibraryVo> pageByQuery(SdAlgorithmLibraryQuery query, PageQuery pageQuery);
}
