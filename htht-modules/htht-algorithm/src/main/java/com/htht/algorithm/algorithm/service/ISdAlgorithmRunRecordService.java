package com.htht.algorithm.algorithm.service;

import com.htht.algorithm.algorithm.domain.SdAlgorithmRunRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationDel;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordAdd;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordQuery;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordUp;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmRunRecordVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
* 算法运行记录表 服务类
*
* @author MSFY
* @since 2026-07-21
*/
public interface ISdAlgorithmRunRecordService extends IService<SdAlgorithmRunRecord> {

    boolean add(SdAlgorithmRunRecordAdd addSdAlgorithmRunRecord);

    boolean up(SdAlgorithmRunRecordUp upSdAlgorithmRunRecord);

    List<SdAlgorithmRunRecordVo> getList(SdAlgorithmRunRecordQuery query);

    SdAlgorithmRunRecordVo getVoById(Long id);

    TableDataInfo<SdAlgorithmRunRecordVo> pageByQuery(SdAlgorithmRunRecordQuery query, PageQuery pageQuery);

    TableDataInfo<SdAlgorithmRunRecordVo> pageById(Long id, PageQuery pageQuery);
}
