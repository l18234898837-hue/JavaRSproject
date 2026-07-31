package com.htht.business.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.business.domain.SysAdministrativeRegions;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsEditVO;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsListVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SysAdministrativeRegionsConverter {
    Page<SysAdministrativeRegionsListVO> entity2page(Page<SysAdministrativeRegions> page);

    SysAdministrativeRegionsEditVO entity2edit(SysAdministrativeRegions sysAdministrativeRegions);

    SysAdministrativeRegions edit2entity(SysAdministrativeRegionsEditVO editVO);
}
