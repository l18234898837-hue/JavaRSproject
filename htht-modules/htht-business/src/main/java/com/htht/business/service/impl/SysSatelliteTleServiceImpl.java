package com.htht.business.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.business.converter.SysSatelliteTleConverter;
import com.htht.business.domain.SysSatelliteTle;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleListVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleQueryVO;
import com.htht.business.mapper.SysSatelliteTleMapper;
import com.htht.business.service.SysSatelliteInfoService;
import com.htht.business.service.SysSatelliteTleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.business.utils.satellite.EpochConverterUtil;
import com.htht.common.build.QueryWrapperBuilder;
import com.htht.common.constant.DateFormatConstant;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.DelFlagEnums;
import com.htht.common.utils.IDUtil;
import lombok.RequiredArgsConstructor;
import org.orekit.propagation.analytical.tle.TLE;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 轨道信息表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-28
 */
@Service
@RequiredArgsConstructor
public class SysSatelliteTleServiceImpl extends ServiceImpl<SysSatelliteTleMapper, SysSatelliteTle> implements SysSatelliteTleService {

    private final SysSatelliteTleConverter sysSatelliteTleConverter;

    private final SysSatelliteInfoService sysSatelliteInfoService;

    @Override
    public TableDataInfo<SysSatelliteTleListVO> pageSysSatelliteTle(SysSatelliteTleQueryVO queryVO) {
        Map<String, Object> params = queryVO.getParams();
        LocalDateTime beginTime = null;
        LocalDateTime endTime = null;
        if (ObjectUtil.isNotNull(params.get("beginTime")) && ObjectUtil.isNotNull(params.get("endTime"))){
            beginTime = LocalDateTimeUtil.parse(params.get("beginTime").toString(), DateFormatConstant.NORMAL);
            endTime = LocalDateTimeUtil.parse(params.get("endTime").toString(), DateFormatConstant.NORMAL);
        }
        Page<SysSatelliteTle> page = this.baseMapper.selectPage(queryVO.build(),
            QueryWrapperBuilder.build(queryVO, SysSatelliteTle.class, false, null, null)
                .ge(beginTime != null, "epoch_time", beginTime)
                .le(endTime != null, "epoch_time", endTime));
        Page<SysSatelliteTleListVO> pagevo = this.sysSatelliteTleConverter.entity2page(page);
        return TableDataInfo.build(pagevo);
    }

    @Override
    public SysSatelliteTleEditVO getSysSatelliteTle(Long id) {
        SysSatelliteTle sysSatelliteTle = this.getById(id);
        return this.sysSatelliteTleConverter.entity2edit(sysSatelliteTle);
    }

    @Override
    public int addSysSatelliteTle(SysSatelliteTleEditVO editVO) {
        SysSatelliteTle sysSatelliteTle = this.sysSatelliteTleConverter.edit2entity(editVO);
        analysisTle(sysSatelliteTle, editVO.getTleLine1(), editVO.getTleLine2());
        if (checkNoradNumber(editVO.getSatelliteInfoId(), sysSatelliteTle.getNoradNumber())) {
            throw new IllegalArgumentException("您所选择卫星的NORAD编号与TLE解析出来的不一致，请修正。如有问题请联系管理员！");
        }
        sysSatelliteTle.setId(IDUtil.getLongID());
        return this.baseMapper.insert(sysSatelliteTle);
    }

    private boolean checkNoradNumber(Long satelliteId, String noradNumber) {
        SysSatelliteInfoEditVO satelliteInfo = this.sysSatelliteInfoService.getSysSatelliteInfo(satelliteId);
        return !noradNumber.trim().equals(satelliteInfo.getNoradNumber().trim());
    }

    /** 解析TLE参数
     * @param line1 tle_line1
     * @param line2 tle_line2
     */
    private void analysisTle(SysSatelliteTle sysSatelliteTle, String line1, String line2) {
        if(!TLE.isFormatOK(line1, line2)) {
            throw new IllegalArgumentException("您所输入的TLE line存在格式问题，请修正。如有问题请联系管理员！");
        }
        TLE tle = new TLE(line1, line2);
        sysSatelliteTle.setNoradNumber(tle.getSatelliteNumber() + "");
        sysSatelliteTle.setEpochTime(EpochConverterUtil.toLocalDateTime(tle.getDate()));
        sysSatelliteTle.setInclination(Math.toDegrees(tle.getI()) + "°");
        sysSatelliteTle.setMeanMotionDot(tle.getMeanMotionFirstDerivative() + " rad/s²");
        sysSatelliteTle.setMeanMotionDdot(tle.getMeanMotionSecondDerivative() + " rad/s³");
        sysSatelliteTle.setBstarDragCoefficient(tle.getBStar() + "");
        sysSatelliteTle.setEphemerisType(tle.getEphemerisType() + "");
        sysSatelliteTle.setElementNumber(tle.getElementNumber() + "");
        sysSatelliteTle.setRightAscension(Math.toDegrees(tle.getRaan()) + "°");
        sysSatelliteTle.setEccentricity(tle.getE() + "");
        sysSatelliteTle.setArgumentOfPerigee(Math.toDegrees(tle.getPerigeeArgument()) + "°");
        sysSatelliteTle.setMeanAnomaly(tle.getMeanAnomaly() + "°");
        sysSatelliteTle.setMeanMotion(tle.getMeanMotion() + " rad/s");
        sysSatelliteTle.setRevolutionNumber( (int) Math.round((2 * Math.PI) / tle.getMeanMotion()) ); //使用Orekit解析的平均运动（弧度/秒）来计算轨道周期
    }

    @Override
    public int updateSysSatelliteTle(SysSatelliteTleEditVO editVO) {
        SysSatelliteTle sysSatelliteTle = this.sysSatelliteTleConverter.edit2entity(editVO);
        analysisTle(sysSatelliteTle, editVO.getTleLine1(), editVO.getTleLine2());
        if (checkNoradNumber(editVO.getSatelliteInfoId(), sysSatelliteTle.getNoradNumber())) {
            throw new IllegalArgumentException("您所选择卫星的NORAD编号与TLE解析出来的不一致，请修正。如有问题请联系管理员！");
        }
        return this.baseMapper.updateById(sysSatelliteTle);
    }

    @Override
    public int updateDeleteStatusByIds(List<Long> ids) {
        return this.baseMapper.update(new SysSatelliteTle(), new LambdaUpdateWrapper<SysSatelliteTle>()
            .in(SysSatelliteTle::getId, ids)
            .set(SysSatelliteTle::getDelFlag, DelFlagEnums.DELETED.getCode()));
    }

    @Override
    public TableDataInfo<SysSatelliteTleListVO> listSysSatelliteTle(List<Long> satelliteInfoIds) {
        List<SysSatelliteTle> list = this.baseMapper.selectList(new LambdaQueryWrapper<SysSatelliteTle>()
            .in(SysSatelliteTle::getSatelliteInfoId, satelliteInfoIds));
        List<SysSatelliteTleListVO> listvo = this.sysSatelliteTleConverter.entity2list(list);
        return TableDataInfo.build(listvo);
    }
}
