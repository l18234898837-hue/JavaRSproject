package com.htht.algorithm.algorithm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.htht.algorithm.algorithm.domain.SdAlgorithmFile;
import com.htht.algorithm.algorithm.domain.SdAlgorithmRunRecord;
import com.htht.algorithm.algorithm.domain.dto.*;
import com.htht.algorithm.algorithm.event.AlgorithmRunRecordEndEvent;
import com.htht.algorithm.algorithm.mapper.SdAlgorithmFileMapper;
import com.htht.algorithm.algorithm.service.ISdAlgorithmFileService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmFileVo;
import com.htht.algorithm.algorithm.service.ISdAlgorithmRunRecordService;
import com.htht.algorithm.download.domain.vo.FileTreeVO;
import com.htht.algorithm.data.utils.FileTreeUtil;
import com.htht.algorithm.algorithm.util.PythonUtil;
import com.htht.algorithm.thread.annotation.Pooled;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.jdcloud.sdk.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 算法文件表 服务实现类
 *
 * @author MSFY
 * @since 2026-07-21
 */
@Service
@RequiredArgsConstructor
public class SdAlgorithmFileServiceImpl extends ServiceImpl<SdAlgorithmFileMapper, SdAlgorithmFile> implements ISdAlgorithmFileService {

    private final ISdAlgorithmRunRecordService  iSdAlgorithmRunRecordService;

    @Override
    public boolean add(SdAlgorithmFileAdd addSdAlgorithmFile) {
        SdAlgorithmFile sdAlgorithmFile = new SdAlgorithmFile();
        BeanUtils.copyProperties(addSdAlgorithmFile, sdAlgorithmFile);
        return save(sdAlgorithmFile);
    }

    @Override
    public List<SdAlgorithmFileVo> getList(SdAlgorithmFileQuery query) {
        return lambdaQuery()
            .like(StringUtils.isNotBlank(query.getAlgorithmName()),SdAlgorithmFile::getAlgorithmName,query.getAlgorithmName())
            .eq(ObjectUtils.isNotNull(query.getAlgorithmType()),SdAlgorithmFile::getAlgorithmType,query.getAlgorithmType())
            .list().stream().map(item -> {
            SdAlgorithmFileVo vo = new SdAlgorithmFileVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean up(SdAlgorithmFileUp upSdAlgorithmFile) {
        SdAlgorithmFile sdAlgorithmFile = new SdAlgorithmFile();
        BeanUtils.copyProperties(upSdAlgorithmFile, sdAlgorithmFile);
        return updateById(sdAlgorithmFile);
    }

    @Override
    public SdAlgorithmFileVo getVoById(Long id) {
        SdAlgorithmFile sdAlgorithmFile = getById(id);
        SdAlgorithmFileVo vo = new SdAlgorithmFileVo();
        BeanUtils.copyProperties(sdAlgorithmFile, vo);
        return vo;
    }

    @Override
    public TableDataInfo<SdAlgorithmFileVo> pageByQuery(SdAlgorithmFileQuery query, PageQuery pageQuery) {
        SdAlgorithmFile sdAlgorithmFile = new SdAlgorithmFile();
        BeanUtils.copyProperties(query, sdAlgorithmFile);
        Page<SdAlgorithmFile> page =lambdaQuery()
            .like(StringUtils.isNotBlank(query.getAlgorithmName()),SdAlgorithmFile::getAlgorithmName,query.getAlgorithmName())
            .eq(ObjectUtils.isNotNull(query.getAlgorithmType()),SdAlgorithmFile::getAlgorithmType,query.getAlgorithmType())
            .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()));
        List<SdAlgorithmFileVo> voList = page.getRecords().stream().map(item -> {
            SdAlgorithmFileVo vo = new SdAlgorithmFileVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
        TableDataInfo<SdAlgorithmFileVo> tableDataInfo = TableDataInfo.build();
        tableDataInfo.setTotal(page.getTotal());
        tableDataInfo.setRows(voList);
        return tableDataInfo;
    }

    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void run(SdAlgorithmStartByVo start) {
        SdAlgorithmFileVo vo = start.getSdAlgorithmFileVo();

        // 1. 安全拆分参数，自动处理null/空字符串，过滤空元素
        List<String> argList = new ArrayList<>();

        // 处理 defaultParams 字符串
        String defaultParamStr = vo.getDefaultParams();
        if (defaultParamStr != null && !defaultParamStr.isBlank()) {
            String[] tempArr = defaultParamStr.trim().split("\\s+");
            argList.addAll(Arrays.asList(tempArr));
        }

        // 处理 variableParams 字符串
        String variableParamStr = start.getParams();
        if (variableParamStr != null && !variableParamStr.isBlank()) {
            String[]tempArr = variableParamStr.trim().split("\\s+");
            argList.addAll(Arrays.asList(tempArr));
        }

        // 转为最终参数数组
        String[] params = argList.toArray(new String[0]);

        switch (vo.getAlgorithmType()) {
            case 0:
                if (vo.getRunPath() == null || vo.getRunPath().isBlank()) {
                    PythonUtil.run(vo.getEnvFilePath(), vo.getFilePath(), true,getRecordEvent(vo), params);
                }else {
                    PythonUtil.run(vo.getEnvFilePath(), vo.getFilePath(), vo.getRunPath(), true,getRecordEvent(vo), params);
                }
                break;
            case 1:
                if (vo.getRunPath() == null || vo.getRunPath().isBlank()) {
                    PythonUtil.runCommand(vo.getFilePath(),true,getRecordEvent(vo), params);
                }else {
                    PythonUtil.runCommand(vo.getFilePath(), vo.getRunPath(), true,getRecordEvent(vo), params);
                }
                break;
            default:
                throw new GlobalException("确保是正确的算法类型");
        }
    }
    private  AlgorithmRunRecordEndEvent getRecordEvent(SdAlgorithmFileVo vo){
        SdAlgorithmRunRecord record = new SdAlgorithmRunRecord().setAlgorithmId(vo.getId()).setStartTime(LocalDateTime.now()).setRunStatus("计算中");
        iSdAlgorithmRunRecordService.save(record);
        return new AlgorithmRunRecordEndEvent().setId(record.getId()).setStartTime(record.getStartTime());
    }

    @Override
    public void run(SdAlgorithmStartById start) {
        SdAlgorithmFileVo vo = getVoById(start.getId());
        SdAlgorithmStartByVo startByVo = new SdAlgorithmStartByVo();
        startByVo.setSdAlgorithmFileVo(vo);
        startByVo.setParams(start.getParams());
        run(startByVo);
    }

    @Override
    public FileTreeVO getInput(Long id) {
        SdAlgorithmFileVo vo = getVoById(id);
        if(vo.getInputFolder() == null || vo.getInputFolder().isBlank()){
            return null;
        }
        return FileTreeUtil.buildFileTree(vo.getInputFolder());
    }

    @Override
    public FileTreeVO getOutput(Long id) {
        SdAlgorithmFileVo vo = getVoById(id);
        if(vo.getOutputFolder()==null || vo.getOutputFolder().isBlank()){
            return null;
        }
        return FileTreeUtil.buildFileTree(vo.getOutputFolder());
    }
}
