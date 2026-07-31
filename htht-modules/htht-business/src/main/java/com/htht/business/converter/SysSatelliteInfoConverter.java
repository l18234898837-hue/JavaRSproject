package com.htht.business.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.business.domain.SysSatelliteInfo;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoListVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SysSatelliteInfoConverter {
    Page<SysSatelliteInfoListVO> entity2page(Page<SysSatelliteInfo> page);

    SysSatelliteInfoEditVO entity2edit(SysSatelliteInfo sysSatelliteInfo);

    SysSatelliteInfo edit2entity(SysSatelliteInfoEditVO editVO);
}
