package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.SdColorRamp;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.vo.ColorRampVo;

/**
 * <p>
 * 热力图色带配置表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2026-03-05
 */
public interface SdColorRampService extends IService<SdColorRamp> {

    ColorRampVo getDefaultColorRamp();

    ColorRampVo getColorRampById(Long id);

}
