package com.htht.algorithm.algorithm.service.impl;

import com.htht.algorithm.algorithm.domain.SdAlgorithmLibrary;
import com.htht.algorithm.algorithm.domain.SdAlgorithmLibraryRelation;
import com.htht.algorithm.algorithm.domain.dto.*;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmFileVo;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmLibraryVo;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmTreeVo;
import com.htht.algorithm.algorithm.mapper.SdAlgorithmLibraryRelationMapper;
import com.htht.algorithm.algorithm.service.ISdAlgorithmFileService;
import com.htht.algorithm.algorithm.service.ISdAlgorithmLibraryRelationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmLibraryRelationVo;
import com.htht.algorithm.algorithm.service.ISdAlgorithmLibraryService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* 算法库与算法文件关联表 服务实现类
*
* @author MSFY
* @since 2026-07-21
*/
@Service
@RequiredArgsConstructor
public class SdAlgorithmLibraryRelationServiceImpl extends ServiceImpl<SdAlgorithmLibraryRelationMapper, SdAlgorithmLibraryRelation> implements ISdAlgorithmLibraryRelationService {
    private final ISdAlgorithmFileService  iSdAlgorithmFileService;
    private final ISdAlgorithmLibraryService  iSdAlgorithmLibraryService;

    @Override
    public boolean add(SdAlgorithmLibraryRelationAdd addSdAlgorithmLibraryRelation) {
        List<SdAlgorithmLibraryRelation> list = lambdaQuery().eq(SdAlgorithmLibraryRelation::getLibraryId, addSdAlgorithmLibraryRelation.getLibraryId())
            .eq(SdAlgorithmLibraryRelation::getAlgorithmFileId, addSdAlgorithmLibraryRelation.getAlgorithmFileId()).list();
        if(!list.isEmpty()){
            throw new GlobalException("无法将算法文件重复添加到算法库内");
        }
        SdAlgorithmLibraryRelation sdAlgorithmLibraryRelation = new SdAlgorithmLibraryRelation();
        BeanUtils.copyProperties(addSdAlgorithmLibraryRelation, sdAlgorithmLibraryRelation);
        return save(sdAlgorithmLibraryRelation);
    }

    @Override
    public List<SdAlgorithmLibraryRelationVo> getList(SdAlgorithmLibraryRelationQuery query) {
        SdAlgorithmLibraryRelation sdAlgorithmLibraryRelation = new SdAlgorithmLibraryRelation();
        BeanUtils.copyProperties(query, sdAlgorithmLibraryRelation);
        return lambdaQuery(sdAlgorithmLibraryRelation).list().stream().map(item -> {
            SdAlgorithmLibraryRelationVo vo = new SdAlgorithmLibraryRelationVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean up(SdAlgorithmLibraryRelationUp upSdAlgorithmLibraryRelation) {
        SdAlgorithmLibraryRelation sdAlgorithmLibraryRelation = new SdAlgorithmLibraryRelation();
        BeanUtils.copyProperties(upSdAlgorithmLibraryRelation, sdAlgorithmLibraryRelation);
        return updateById(sdAlgorithmLibraryRelation);
    }

    @Override
    public SdAlgorithmLibraryRelationVo getVoById(Long id) {
        SdAlgorithmLibraryRelation sdAlgorithmLibraryRelation = getById(id);
        SdAlgorithmLibraryRelationVo vo = new SdAlgorithmLibraryRelationVo();
        BeanUtils.copyProperties(sdAlgorithmLibraryRelation, vo);
        return vo;
    }

    @Override
    public TableDataInfo<SdAlgorithmLibraryRelationVo> pageByQuery(SdAlgorithmLibraryRelationQuery query, PageQuery pageQuery) {
        SdAlgorithmLibraryRelation sdAlgorithmLibraryRelation = new SdAlgorithmLibraryRelation();
        BeanUtils.copyProperties(query, sdAlgorithmLibraryRelation);
        Page<SdAlgorithmLibraryRelation> page = lambdaQuery(sdAlgorithmLibraryRelation).page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()));
        List<SdAlgorithmLibraryRelationVo> voList = page.getRecords().stream().map(item -> {
            SdAlgorithmLibraryRelationVo vo = new SdAlgorithmLibraryRelationVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
        TableDataInfo<SdAlgorithmLibraryRelationVo> tableDataInfo =TableDataInfo.build();
        tableDataInfo.setTotal(page.getTotal());
        tableDataInfo.setRows(voList);
        return tableDataInfo;
    }

    @Override
    public List<SdAlgorithmTreeVo> tree() {
        List<SdAlgorithmTreeVo> tree=new ArrayList<>();
        List<SdAlgorithmLibraryVo> sdAlgorithmLibraries = iSdAlgorithmLibraryService.getList(new SdAlgorithmLibraryQuery());
        sdAlgorithmLibraries.forEach(sdAlgorithmLibrary -> {
            SdAlgorithmTreeVo sdAlgorithmTreeVo = new SdAlgorithmTreeVo();
            sdAlgorithmTreeVo.setSdAlgorithmLibrary(sdAlgorithmLibrary);
            List<SdAlgorithmFileVo> fileList = getList(new SdAlgorithmLibraryRelationQuery().setLibraryId(sdAlgorithmLibrary.getId())).stream().map(
                v -> iSdAlgorithmFileService.getVoById(v.getAlgorithmFileId())
            ).toList();
            sdAlgorithmTreeVo.setSdAlgorithmFiles(fileList);
            tree.add(sdAlgorithmTreeVo);
        });
        return tree;
    }

    @Override
    public boolean removeByDel(SdAlgorithmLibraryRelationDel del) {
        SdAlgorithmLibraryRelation relation = lambdaQuery().eq(SdAlgorithmLibraryRelation::getLibraryId, del.getLibraryId())
            .eq(SdAlgorithmLibraryRelation::getAlgorithmFileId, del.getAlgorithmFileId()).one();
        return removeById(relation.getId());
    }
}
