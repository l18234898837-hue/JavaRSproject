package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.SdWindFileIndex;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.vo.WDSDQuery;
import me.msfy.windsd.domain.WindData;

import java.util.List;

/**
 * <p>
 * 风向记录 服务类
 * </p>
 *
 * @author MSFY
 * @since 2026-05-20
 */
public interface SdWindFileIndexService extends IService<SdWindFileIndex> {

    void checkData(WDSDQuery query);

    List<WindData> genData(WDSDQuery query) throws Exception;
}
