package com.htht.algorithm.data.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.domain.*;
import com.htht.algorithm.data.domain.vo.StSubmitAuditRecordDataVo;
import com.htht.algorithm.data.domain.vo.StSubmitAuditRecordVo;
import com.htht.algorithm.data.event.StCalculationResultApplyLogEvent;
import com.htht.algorithm.data.event.StCalculationResultApplyLogUpdateStatusOneEvent;
import com.htht.algorithm.data.mapper.StSubmitAuditRecordMapper;
import com.htht.algorithm.data.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 提交数据审核表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-30
 */
@Service
@RequiredArgsConstructor
public class StSubmitAuditRecordServiceImpl extends ServiceImpl<StSubmitAuditRecordMapper, StSubmitAuditRecord> implements StSubmitAuditRecordService {
    private final StSubmitAuditRecordDataService stSubmitAuditRecordDataService;
    private final IBusDataInfoService  busDataInfoService;
    private final IBusFileInfoService busFileInfoService;
    private final StDataTypeService stDataTypeService;
    private final IBusCatalogInfoService busCatalogInfoService;

    private final IBusCatalogDataInfoService catalogDataInfoService;

    @Override
    public void add(StSubmitAuditRecord record, List<StAlgorithmRecordCatalogInfo> list) {
        StringBuffer nameKey=new StringBuffer();
        list.forEach(info -> {
            nameKey.append(busDataInfoService.getDataInfoById(info.getDataId()).getName()).append(",");
        });
        record.setNameKey(nameKey.toString());
        save( record);
        List<StSubmitAuditRecordData> recordDataList = list.stream().map(info -> {
            return new StSubmitAuditRecordData().setRecordId(record.getId())
                    .setAuditStatus(Short.valueOf("1"))
                    .setDataId(info.getDataId())
                    .setDataTypeId(info.getDataTypeId());
        }).toList();
        stSubmitAuditRecordDataService.saveBatch(recordDataList);
        recordDataList.forEach(
                info->{
                    BusDataInfo dataInfo = busDataInfoService.getDataInfoById(info.getDataId());
                    BusFileInfo fileInfo = busFileInfoService.getFileInfoById(dataInfo.getFileId());
                    StDataType currentType = stDataTypeService.getById(info.getDataTypeId());
                    StDataType parentType = stDataTypeService.getById(currentType.getPid());
                    new StCalculationResultApplyLogEvent()
                            .setUserId(record.getSubmitUserId())
                            .setDataName(dataInfo.getName())
                            .setDataType(fileInfo.getExtension())
                            .setSatelliteSensor(parentType.getName())
                            .setDataLevel(currentType.getName())
                            .setRegion(record.getRegion())
                            .setApplyStatus(Short.valueOf("1"))
                            .setApplyGroupId(record.getId())
                            .setDataId(info.getDataId())
                            .sendEvent();
                }
        );
    }

    @Override
    public boolean delectBy(Long id) {
        stSubmitAuditRecordDataService.lambdaUpdate()
                .eq(StSubmitAuditRecordData::getRecordId,id)
                .remove();
        return removeById(id);
    }

    @Override
    public TableDataInfo<StSubmitAuditRecordVo> page(Long status, String nameKey, PageQuery pageQuery) {
        Page<StSubmitAuditRecord> page = lambdaQuery()
                .eq(ObjectUtil.isNotNull(status), StSubmitAuditRecord::getAuditStatus, status)
                .and(StringUtils.isNotBlank(nameKey), wrapper -> wrapper
                        .like(StSubmitAuditRecord::getRegion, nameKey)
                        .or()
                        .like(StSubmitAuditRecord::getNameKey, nameKey)
                        .or()
                        .like(StSubmitAuditRecord::getCreateBy, nameKey))
                .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()));
        List<StSubmitAuditRecordVo> list = page.getRecords().stream().map(this::convertToVo).toList();

        return TableDataInfo.build(list);
    }

    @Override
    public void audit(Long recordId, Integer status) {
        List<StSubmitAuditRecordData> list = stSubmitAuditRecordDataService.lambdaQuery().eq(StSubmitAuditRecordData::getRecordId, recordId).list();
        list.forEach(info -> {
            auditData(recordId, info.getDataId(), status);
        });
    }

    @Override
    public void auditData(Long recordId, Long dataId, Integer status) {
        StSubmitAuditRecordData recordData = stSubmitAuditRecordDataService.lambdaQuery()
                .eq(StSubmitAuditRecordData::getRecordId, recordId)
                .eq(StSubmitAuditRecordData::getDataId, dataId)
                .one();
        if (recordData.getAuditStatus().equals(Short.valueOf("1"))) {
            checkAudit(recordId);
            recordData.setAuditStatus(Short.valueOf(status.toString()));
            submitPublic(recordData);
            stSubmitAuditRecordDataService.lambdaUpdate()
                    .eq(StSubmitAuditRecordData::getRecordId, recordId)
                    .eq(StSubmitAuditRecordData::getDataId, dataId)
                    .update(recordData);
            StCalculationResultApplyLogUpdateStatusOneEvent stCalculationResultApplyLogUpdateStatusOneEvent = new StCalculationResultApplyLogUpdateStatusOneEvent();
            stCalculationResultApplyLogUpdateStatusOneEvent
                    .setApplyGroupId(recordId)
                    .setApplyStatus(Short.valueOf(status.toString()))
                    .setDataId(dataId)
                    .sendEvent();
            checkRecord(recordId);
        }
    }



    private void submitPublic(StSubmitAuditRecordData recordData) {
        if(recordData.getAuditStatus().equals(Short.valueOf("2"))){
            StDataType dataType = stDataTypeService.getById(recordData.getDataTypeId());
            BusCatalogDataInfo one = catalogDataInfoService.lambdaQuery().eq(BusCatalogDataInfo::getDataId, recordData.getDataId())
                    .eq(BusCatalogDataInfo::getCatalogId, dataType.getProductCatalogId()).one();
            if(one==null){
                busCatalogInfoService.bindDataToCatalog(recordData.getDataId(), dataType.getProductCatalogId());
            }
             one = catalogDataInfoService.lambdaQuery().eq(BusCatalogDataInfo::getDataId, recordData.getDataId())
                    .eq(BusCatalogDataInfo::getCatalogId, dataType.getCalculateCatalogId()).one();
            if(one==null){
                busCatalogInfoService.bindDataToCatalog(recordData.getDataId(), dataType.getCalculateCatalogId());
            }
        }
    }
    private void checkRecord(Long recordId) {
        List<StSubmitAuditRecordData> list = stSubmitAuditRecordDataService.lambdaQuery().eq(StSubmitAuditRecordData::getRecordId, recordId)
                .eq(StSubmitAuditRecordData::getAuditStatus, Short.valueOf("1")).list();
        if(list.isEmpty()){
            List<StSubmitAuditRecordData> agreeList = stSubmitAuditRecordDataService.lambdaQuery().eq(StSubmitAuditRecordData::getRecordId, recordId)
                    .eq(StSubmitAuditRecordData::getAuditStatus, Short.valueOf("2")).list();
            StSubmitAuditRecord record = getById(recordId);
            if(agreeList.isEmpty()){
                record.setAuditStatus(Short.valueOf("4"));
            }else if( agreeList.size()==record.getSubmitCount()){
                record.setAuditStatus(Short.valueOf("3"));
            }else{
                record.setAuditStatus(Short.valueOf("2"));
            }
            updateById(record);
        }
    }
    private void checkAudit(Long recordId){
        StSubmitAuditRecord record = getById(recordId);
        if(!record.getAuditStatus().equals(Short.valueOf("1"))){
            throw new GlobalException("以审核过，操作无效");
        }
    }

    private StSubmitAuditRecordVo convertToVo(StSubmitAuditRecord record){
        StSubmitAuditRecordVo vo = new StSubmitAuditRecordVo();
        vo.setId(record.getId())
                .setUserName(record.getCreateBy())
                .setSubmitTime(record.getCreateTime())
                .setSubmitCount(record.getSubmitCount())
                .setAuditReason(record.getAuditReason())
                .setAuditStatus(record.getAuditStatus());
        List<StSubmitAuditRecordData> dataList = stSubmitAuditRecordDataService.lambdaQuery()
                .eq(StSubmitAuditRecordData::getRecordId,record.getId())
                .list();
        List<StSubmitAuditRecordDataVo> list = dataList.stream().map(this::convertToDataVo).toList();
        vo.setDataVoList(list);
        return vo;
    }

    private StSubmitAuditRecordDataVo convertToDataVo(StSubmitAuditRecordData  data){
        StSubmitAuditRecordDataVo dataVo = new StSubmitAuditRecordDataVo();
        StSubmitAuditRecord record = getById(data.getRecordId());
        BusDataInfo dataInfo = busDataInfoService.getById(data.getDataId());
        BusFileInfo fileInfo = busFileInfoService.getById(dataInfo.getFileId());
        StDataType currentType = stDataTypeService.getById(data.getDataTypeId());
        StDataType parent = stDataTypeService.getParent(currentType.getPid());
        return dataVo.setRecordId(data.getRecordId())
                .setDataId(data.getDataId())
                .setDataName(dataInfo.getName())
                .setDataType(fileInfo.getExtension())
                .setSatelliteSensor(parent.getName())
                .setDataLevel(currentType.getName())
                .setRegion(record.getRegion())
                .setAuditStatus(data.getAuditStatus());
    }
}
