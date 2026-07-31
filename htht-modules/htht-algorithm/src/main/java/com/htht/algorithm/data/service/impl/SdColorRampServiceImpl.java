package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.domain.SdColorRamp;
import com.htht.algorithm.data.domain.vo.ColorRampVo;
import com.htht.algorithm.data.mapper.SdColorRampMapper;
import com.htht.algorithm.data.service.SdColorRampService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.exception.GlobalException;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 热力图色带配置表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2026-03-05
 */
@Service
public class SdColorRampServiceImpl extends ServiceImpl<SdColorRampMapper, SdColorRamp> implements SdColorRampService {

    @Override
    public ColorRampVo getDefaultColorRamp() {
        SdColorRamp defaultColorRamp = getById(1);
        if(defaultColorRamp==null){
            throw new GlobalException("未找到默认色带");
        }
        return ColorRampVo.fromSdColorRamp(defaultColorRamp);
    }

    @Override
    public ColorRampVo getColorRampById(Long id) {
        SdColorRamp defaultColorRamp = getById(id);
        if(defaultColorRamp==null){
            throw new GlobalException("未找到传入色带");
        }
        return ColorRampVo.fromSdColorRamp(defaultColorRamp);
    }
}
