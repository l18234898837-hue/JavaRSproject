package com.htht.algorithm.algorithm.service.impl;

import com.htht.algorithm.algorithm.domain.SdAlgorithmLibrary;
import com.htht.algorithm.algorithm.mapper.SdAlgorithmLibraryMapper;
import com.htht.algorithm.algorithm.service.ISdAlgorithmLibraryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryAdd;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryQuery;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryUp;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmLibraryVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.jdcloud.sdk.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* 算法库表 服务实现类
*
* @author MSFY
* @since 2026-07-21
*/
@Service
public class SdAlgorithmLibraryServiceImpl extends ServiceImpl<SdAlgorithmLibraryMapper, SdAlgorithmLibrary> implements ISdAlgorithmLibraryService {

    @Override
    public boolean add(SdAlgorithmLibraryAdd addSdAlgorithmLibrary) {
        SdAlgorithmLibrary sdAlgorithmLibrary = new SdAlgorithmLibrary();
        BeanUtils.copyProperties(addSdAlgorithmLibrary, sdAlgorithmLibrary);
        return save(sdAlgorithmLibrary);
    }

    @Override
    public List<SdAlgorithmLibraryVo> getList(SdAlgorithmLibraryQuery query) {
        return lambdaQuery()
            .like(StringUtils.isNotBlank(query.getLibraryName()),SdAlgorithmLibrary::getLibraryName,query.getLibraryName())
            .orderByAsc(SdAlgorithmLibrary::getSortOrder)
            .list().stream().map(item -> {
            SdAlgorithmLibraryVo vo = new SdAlgorithmLibraryVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean up(SdAlgorithmLibraryUp upSdAlgorithmLibrary) {
        SdAlgorithmLibrary sdAlgorithmLibrary = new SdAlgorithmLibrary();
        BeanUtils.copyProperties(upSdAlgorithmLibrary, sdAlgorithmLibrary);
        return updateById(sdAlgorithmLibrary);
    }

    @Override
    public SdAlgorithmLibraryVo getVoById(Long id) {
        SdAlgorithmLibrary sdAlgorithmLibrary = getById(id);
        SdAlgorithmLibraryVo vo = new SdAlgorithmLibraryVo();
        BeanUtils.copyProperties(sdAlgorithmLibrary, vo);
        return vo;
    }

    @Override
    public TableDataInfo<SdAlgorithmLibraryVo> pageByQuery(SdAlgorithmLibraryQuery query, PageQuery pageQuery) {
        Page<SdAlgorithmLibrary> page = lambdaQuery()
            .like(StringUtils.isNotBlank(query.getLibraryName()),SdAlgorithmLibrary::getLibraryName,query.getLibraryName())
            .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()));
        List<SdAlgorithmLibraryVo> voList = page.getRecords().stream().map(item -> {
            SdAlgorithmLibraryVo vo = new SdAlgorithmLibraryVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
        TableDataInfo<SdAlgorithmLibraryVo> tableDataInfo =TableDataInfo.build();
        tableDataInfo.setTotal(page.getTotal());
        tableDataInfo.setRows(voList);
        return tableDataInfo;
    }
}
