package com.htht.business.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.business.domain.SdObserveData;
import com.htht.business.domain.dto.QuerySdObserveDataDto;
import com.htht.business.domain.vo.SdObserveDataVo;
import com.htht.business.domain.vo.SdObserveIntervalVo;
import com.htht.business.mapper.SdObserveDataMapper;
import com.htht.business.service.SdObserveDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.business.utils.ExcelUtil;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 卫星观测的详细数据记录表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-10-30
 */
@Service
public class SdObserveDataServiceImpl extends ServiceImpl<SdObserveDataMapper, SdObserveData> implements SdObserveDataService {
    @Override
    public TableDataInfo<SdObserveDataVo> query(QuerySdObserveDataDto query, PageQuery pageQuery) {
        Page<SdObserveDataVo> page=Page.of(pageQuery.getPageNum(),pageQuery.getPageSize());
        IPage<SdObserveDataVo> observeListByCondition = baseMapper.getObserveListByCondition(page, query);
        return TableDataInfo.build(observeListByCondition);
    }

    @Override
    public List<String> querySite() {
        return lambdaQuery().select(SdObserveData::getSite).groupBy(SdObserveData::getSite).isNotNull(SdObserveData::getSite).list().stream().map(SdObserveData::getSite).toList();
    }

    @Override
    public List<String> queryDevice() {
        return lambdaQuery().select(SdObserveData::getDeviceModel).groupBy(SdObserveData::getDeviceModel).isNotNull(SdObserveData::getDeviceModel).list().stream().map(SdObserveData::getDeviceModel).toList();
    }

    @Override
    public SdObserveIntervalVo getInterval() {
        // 1. 查询主表数据
        SdObserveIntervalVo vo = baseMapper.getObserveDataMaxMin();

        // 2. 查询波长数据
        SdObserveIntervalVo wavelengthVo = baseMapper.getWavelengthMaxMin();

        // 3. 把波长的最大最小值设置进去
        if (wavelengthVo != null) {
            vo.setWavelengthStart(wavelengthVo.getWavelengthStart());
            vo.setWavelengthEnd(wavelengthVo.getWavelengthEnd());
        }
        return vo;
    }
}
