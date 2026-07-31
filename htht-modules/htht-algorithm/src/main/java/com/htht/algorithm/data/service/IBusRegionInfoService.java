package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusRegionInfo;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-05-29
 */
public interface IBusRegionInfoService extends IService<BusRegionInfo> {

    /**
     * 构建行政区划树
     *
     * @return
     */
    List<BusRegionInfo> buildTree();
}
