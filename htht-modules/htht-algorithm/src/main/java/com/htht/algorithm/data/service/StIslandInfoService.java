package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StIslandInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.vo.StIslandInfoQueryVO;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 存储群岛信息，含层级关系（parent_id和level） 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-18
 */
public interface StIslandInfoService extends IService<StIslandInfo> {
     boolean add( StIslandInfo stIslandInfo);

     boolean addBatch( List<StIslandInfo> islandList);

     List<StIslandInfo> buildTree();

    TableDataInfo<StIslandInfo> pageStIslandInfo(StIslandInfoQueryVO queryVO);

    StIslandInfo getStIslandInfo(Long id);
}
