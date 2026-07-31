package com.htht.algorithm.algorithm.service;

import com.htht.algorithm.algorithm.domain.SdAlgorithmLibraryRelation;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationAdd;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationDel;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationQuery;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationUp;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmLibraryRelationVo;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmTreeVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
* 算法库与算法文件关联表 服务类
*
* @author MSFY
* @since 2026-07-21
*/
public interface ISdAlgorithmLibraryRelationService extends IService<SdAlgorithmLibraryRelation> {

    boolean add(SdAlgorithmLibraryRelationAdd addSdAlgorithmLibraryRelation);

    boolean up(SdAlgorithmLibraryRelationUp upSdAlgorithmLibraryRelation);

    List<SdAlgorithmLibraryRelationVo> getList(SdAlgorithmLibraryRelationQuery query);

    SdAlgorithmLibraryRelationVo getVoById(Long id);

    TableDataInfo<SdAlgorithmLibraryRelationVo> pageByQuery(SdAlgorithmLibraryRelationQuery query, PageQuery pageQuery);

    List<SdAlgorithmTreeVo> tree();

    boolean removeByDel(SdAlgorithmLibraryRelationDel del);
}
