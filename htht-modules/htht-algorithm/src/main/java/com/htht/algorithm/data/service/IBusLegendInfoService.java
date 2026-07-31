package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusLegendInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-06-05
 */
public interface IBusLegendInfoService extends IService<BusLegendInfo> {


    /**
     * 新增配色
     *
     * @param legendInfo
     * @return
     */
    int addLegend(BusLegendInfo legendInfo);

    /**
     * 删除配色
     *
     * @param id
     * @return
     */
    int removeLegend(Long id);

    /**
     * 更新配色
     *
     * @param legendInfo
     * @return
     */
    int updateLegend(BusLegendInfo legendInfo);

    /**
     * 根据类型和数据ID或算法ID分页查询配色
     *
     * @param type
     * @param id
     * @return
     */
    List<BusLegendInfo> getLegendList(int type, Long id);

    /**
     * 根据类型和数据ID查询启用的配色
     *
     * @param type
     * @param id
     * @return
     */
    BusLegendInfo getEnabledLegend(int type, Long id);
    BusLegendInfo getEnabledLegend2(int type, Long id,String dataType);
    /**
     * 启用或关闭配色
     *
     * @param id
     * @param enabled
     */
    void enabledLegend(Long id, Boolean enabled);

    /**
     * 导入sld样式文件
     *
     * @param file
     */
    List<Map<String,Object>> importSld(MultipartFile file) throws Exception;

}
