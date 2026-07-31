package com.htht.algorithm.algorithm.service.impl;

import com.htht.algorithm.algorithm.domain.SdAlgorithmRunRecord;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationDel;
import com.htht.algorithm.algorithm.event.AlgorithmRunRecordEndEvent;
import com.htht.algorithm.algorithm.mapper.SdAlgorithmRunRecordMapper;
import com.htht.algorithm.algorithm.service.ISdAlgorithmRunRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordAdd;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordQuery;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordUp;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmRunRecordVo;
import com.htht.algorithm.base.utils.ConvertUtil;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* 算法运行记录表 服务实现类
*
* @author MSFY
* @since 2026-07-21
*/
@Service
public class SdAlgorithmRunRecordServiceImpl extends ServiceImpl<SdAlgorithmRunRecordMapper, SdAlgorithmRunRecord> implements ISdAlgorithmRunRecordService {

    @Override
    public boolean add(SdAlgorithmRunRecordAdd addSdAlgorithmRunRecord) {
        SdAlgorithmRunRecord sdAlgorithmRunRecord = new SdAlgorithmRunRecord();
        BeanUtils.copyProperties(addSdAlgorithmRunRecord, sdAlgorithmRunRecord);
        return save(sdAlgorithmRunRecord);
    }

    @Override
    public List<SdAlgorithmRunRecordVo> getList(SdAlgorithmRunRecordQuery query) {
        SdAlgorithmRunRecord sdAlgorithmRunRecord = new SdAlgorithmRunRecord();
        BeanUtils.copyProperties(query, sdAlgorithmRunRecord);
        return lambdaQuery(sdAlgorithmRunRecord).list().stream().map(item -> {
            SdAlgorithmRunRecordVo vo = new SdAlgorithmRunRecordVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean up(SdAlgorithmRunRecordUp upSdAlgorithmRunRecord) {
        SdAlgorithmRunRecord sdAlgorithmRunRecord = new SdAlgorithmRunRecord();
        BeanUtils.copyProperties(upSdAlgorithmRunRecord, sdAlgorithmRunRecord);
        return updateById(sdAlgorithmRunRecord);
    }

    @Override
    public SdAlgorithmRunRecordVo getVoById(Long id) {
        SdAlgorithmRunRecord sdAlgorithmRunRecord = getById(id);
        SdAlgorithmRunRecordVo vo = new SdAlgorithmRunRecordVo();
        BeanUtils.copyProperties(sdAlgorithmRunRecord, vo);
        return vo;
    }

    @Override
    public TableDataInfo<SdAlgorithmRunRecordVo> pageByQuery(SdAlgorithmRunRecordQuery query, PageQuery pageQuery) {
        SdAlgorithmRunRecord sdAlgorithmRunRecord = new SdAlgorithmRunRecord();
        BeanUtils.copyProperties(query, sdAlgorithmRunRecord);
        Page<SdAlgorithmRunRecord> page = lambdaQuery(sdAlgorithmRunRecord).page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()));
        return getSdAlgorithmRunRecordVoTableDataInfo(page);
    }

    @Override
    public TableDataInfo<SdAlgorithmRunRecordVo> pageById(Long id, PageQuery pageQuery) {
        Page<SdAlgorithmRunRecord> page = lambdaQuery().eq(SdAlgorithmRunRecord::getAlgorithmId,id).orderByDesc(SdAlgorithmRunRecord::getCreateTime).page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()));
        return getSdAlgorithmRunRecordVoTableDataInfo(page);
    }

    @NotNull
    private TableDataInfo<SdAlgorithmRunRecordVo> getSdAlgorithmRunRecordVoTableDataInfo(Page<SdAlgorithmRunRecord> page) {
        List<SdAlgorithmRunRecordVo> voList = page.getRecords().stream().map(item -> {
            SdAlgorithmRunRecordVo vo = new SdAlgorithmRunRecordVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
        TableDataInfo<SdAlgorithmRunRecordVo> tableDataInfo =TableDataInfo.build();
        tableDataInfo.setTotal(page.getTotal());
        tableDataInfo.setRows(voList);
        return tableDataInfo;
    }


    /**
     * 承接事件类
     */
    @Async
    @EventListener
    public void recordRunEndLog(AlgorithmRunRecordEndEvent event){
        SdAlgorithmRunRecord record = ConvertUtil.convert(event, SdAlgorithmRunRecord.class);
        updateById(record);
    }
}
