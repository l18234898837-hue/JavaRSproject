package com.htht.algorithm.data.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.htht.algorithm.data.domain.*;
import com.htht.algorithm.data.domain.vo.AddAlgorithmTaskVo;
import com.htht.algorithm.data.domain.vo.SubmitPublicVo;
import com.htht.algorithm.data.domain.vo.SubmitUserVo;
import com.htht.algorithm.data.mapper.StAlgorithmRecordCatalogInfoMapper;
import com.htht.algorithm.data.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.helper.LoginHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-29
 */
@Service
@RequiredArgsConstructor
public class StAlgorithmRecordCatalogInfoServiceImpl extends ServiceImpl<StAlgorithmRecordCatalogInfoMapper, StAlgorithmRecordCatalogInfo> implements StAlgorithmRecordCatalogInfoService {
    private final IBusDataInfoService busDataInfoService;
    private final StDataTypeService stDataTypeService;
    private final IBusCatalogDataInfoService catalogDataInfoService;
    private final StUserCatalogDataInfoService stUserCatalogDataInfoService;
    private final StSubmitAuditRecordDataService stSubmitAuditRecordDataService;
    private final StSubmitAuditRecordService stSubmitAuditRecordService;


    @Override
    public boolean bind(Long dataTypeId,
                        Long algorithmId,
                        Long dataId) {
        StAlgorithmRecordCatalogInfo recordCatalogInfo = new StAlgorithmRecordCatalogInfo();
        recordCatalogInfo.setAlgorithmId(algorithmId);
        recordCatalogInfo.setDataTypeId(dataTypeId);
        recordCatalogInfo.setDataId(dataId);
        return save(recordCatalogInfo);
    }

    @Override
    public void delectByAlgorithmId(Long algorithmRecordId) {
        List<StAlgorithmRecordCatalogInfo> list = lambdaQuery().
            eq(StAlgorithmRecordCatalogInfo::getAlgorithmId, algorithmRecordId).list();
        list.forEach(this::removeDataInfo);
        lambdaUpdate().eq(StAlgorithmRecordCatalogInfo::getAlgorithmId, algorithmRecordId).remove();
    }

    private void removeDataInfo(StAlgorithmRecordCatalogInfo info) {
        List<BusCatalogDataInfo> publicList = catalogDataInfoService.lambdaQuery().eq(BusCatalogDataInfo::getDataId, info.getDataId()).list();
        List<StUserCatalogDataInfo> userList = stUserCatalogDataInfoService.lambdaQuery().eq(StUserCatalogDataInfo::getDataId, info.getDataId()).list();
        List<StSubmitAuditRecordData> auditList = stSubmitAuditRecordDataService.lambdaQuery().eq(StSubmitAuditRecordData::getDataId, info.getDataId()).list();
        if (publicList.isEmpty() && userList.isEmpty() && auditList.isEmpty()) {
            busDataInfoService.removeDataInfo(info.getDataId());
        }
    }

    @Override
    public Map<String, Map<String, List<BusDataInfo>>> getVoByAlgorithmId(Long algorithmRecordId) {
        List<StAlgorithmRecordCatalogInfo> arclist = lambdaQuery()
            .eq(StAlgorithmRecordCatalogInfo::getAlgorithmId, algorithmRecordId)
            .list();
        Map<Long, List<Long>> returnMapOne = arclist.stream()
            .collect(Collectors.groupingBy(
                StAlgorithmRecordCatalogInfo::getDataTypeId,
                Collectors.mapping(
                    StAlgorithmRecordCatalogInfo::getDataId,
                    Collectors.toList()
                )
            ));

        Map<String, Map<String, List<Long>>> returnTwoMap = new HashMap<>();

        for (Map.Entry<Long, List<Long>> entry : returnMapOne.entrySet()) {
            StDataType currentDataType = stDataTypeService.getById(entry.getKey());
            StDataType parent = stDataTypeService.getParent(currentDataType.getPid());
            Map<String, List<Long>> childMap = returnTwoMap.get(parent.getName());
            if (ObjectUtil.isNull(childMap)) {
                childMap = new HashMap<>();
                childMap.put(currentDataType.getName(), entry.getValue());
                returnTwoMap.put(parent.getName(), childMap);
            } else {
                childMap.put(currentDataType.getName(), entry.getValue());
            }
        }

        Map<String, Map<String, List<BusDataInfo>>> returnMap = new HashMap<>();
        returnTwoMap.forEach((parentName, childMap) -> {
            Map<String, List<BusDataInfo>> childReturnMap = new HashMap<>();
            childMap.forEach((childName, dataIds) -> {
                List<BusDataInfo> dataInfoList = busDataInfoService.lambdaQuery().in(BusDataInfo::getId, dataIds).list();
                childReturnMap.put(childName, dataInfoList);
            });
            returnMap.put(parentName, childReturnMap);
        });

        return returnMap;
    }

    @Override
    public void submitUser( SubmitUserVo suv) {
        Long userId = LoginHelper.getUserId();
        for (Long dataId : suv.getDataIds()) {
            StAlgorithmRecordCatalogInfo recordCatalogInfo = lambdaQuery().eq(StAlgorithmRecordCatalogInfo::getDataId, dataId).one();
            StDataType dataType = stDataTypeService.getById(recordCatalogInfo.getDataTypeId());
            stUserCatalogDataInfoService.bind(dataType.getCalculateCatalogId(), userId, dataId);
            stUserCatalogDataInfoService.bind(dataType.getProductCatalogId(), userId, dataId);
        }
    }

    @Override
    public void submitPublic(SubmitPublicVo spv) {
        StSubmitAuditRecord record = new StSubmitAuditRecord();
        record.setAuditStatus(Short.valueOf("1"));
        record.setSubmitUserId(LoginHelper.getUserId());
        record.setAuditReason(spv.getReason());
        record.setRegion(spv.getRegion());
        record.setSubmitCount(spv.getDataIds().size());
        List<StAlgorithmRecordCatalogInfo> list = spv.getDataIds().stream().map(dataId -> {
            return lambdaQuery().eq(StAlgorithmRecordCatalogInfo::getDataId, dataId).one();
        }).toList();
        stSubmitAuditRecordService.add(record, list);

    }
}
