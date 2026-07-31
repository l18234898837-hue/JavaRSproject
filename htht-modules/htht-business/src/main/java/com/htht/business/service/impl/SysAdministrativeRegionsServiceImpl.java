package com.htht.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.business.converter.SysAdministrativeRegionsConverter;
import com.htht.business.domain.SysAdministrativeRegions;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsEditVO;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsListVO;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsQueryVO;
import com.htht.business.mapper.SysAdministrativeRegionsMapper;
import com.htht.business.service.SysAdministrativeRegionsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.build.QueryWrapperBuilder;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.DelFlagEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 行政区域信息表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-25
 */
@Service
@RequiredArgsConstructor
public class SysAdministrativeRegionsServiceImpl extends ServiceImpl<SysAdministrativeRegionsMapper, SysAdministrativeRegions> implements SysAdministrativeRegionsService {

    private final SysAdministrativeRegionsConverter sysAdministrativeRegionsConverter;

    @Override
    public TableDataInfo<SysAdministrativeRegionsListVO> pageSysAdministrativeRegions(SysAdministrativeRegionsQueryVO queryVO) {
        Page<SysAdministrativeRegions> page = this.baseMapper.selectPage(queryVO.build(), QueryWrapperBuilder.build(queryVO, SysAdministrativeRegions.class, false, null, null));
        Page<SysAdministrativeRegionsListVO> pagevo = this.sysAdministrativeRegionsConverter.entity2page(page);
        return TableDataInfo.build(pagevo);
    }

    @Override
    public SysAdministrativeRegionsEditVO getSysAdministrativeRegions(Long id) {
        SysAdministrativeRegions sysAdministrativeRegions = this.baseMapper.selectById(id);
        return this.sysAdministrativeRegionsConverter.entity2edit(sysAdministrativeRegions);
    }

    @Override
    public int addSysAdministrativeRegions(SysAdministrativeRegionsEditVO editVO) {
        SysAdministrativeRegions sysAdministrativeRegions = this.sysAdministrativeRegionsConverter.edit2entity(editVO);
        return this.baseMapper.insert(sysAdministrativeRegions);
    }

    @Override
    public int updateSysAdministrativeRegions(SysAdministrativeRegionsEditVO editVO) {
        SysAdministrativeRegions sysAdministrativeRegions = this.sysAdministrativeRegionsConverter.edit2entity(editVO);
        return this.baseMapper.updateById(sysAdministrativeRegions);
    }

    @Override
    public int updateDeleteStatusByIds(List<Long> ids) {
        return this.baseMapper.update(new SysAdministrativeRegions(), new LambdaUpdateWrapper<SysAdministrativeRegions>()
            .in(SysAdministrativeRegions::getId, ids)
            .set(SysAdministrativeRegions::getDelFlag, DelFlagEnums.DELETED.getCode()));
    }
}
