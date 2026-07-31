package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StAlgorithmRecordCatalogInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.vo.AddAlgorithmTaskVo;
import com.htht.algorithm.data.domain.vo.SubmitPublicVo;
import com.htht.algorithm.data.domain.vo.SubmitUserVo;

import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-29
 */
public interface StAlgorithmRecordCatalogInfoService extends IService<StAlgorithmRecordCatalogInfo> {
    boolean bind(Long dataTypeId,
                 Long algorithmId,
                 Long dataId);
    void delectByAlgorithmId(Long algorithmRecordId);
    Map<String, Map<String, List<BusDataInfo>>> getVoByAlgorithmId(Long algorithmRecordId);

    void submitUser( SubmitUserVo suv);

    void submitPublic(SubmitPublicVo spv);
}
