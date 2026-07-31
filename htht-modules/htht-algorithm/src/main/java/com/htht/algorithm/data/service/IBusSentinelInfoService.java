package com.htht.algorithm.data.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusSentinelInfo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-07-18
 */
public interface IBusSentinelInfoService extends IService<BusSentinelInfo> {

    /**
     * 新增在线资源
     *
     * @param sentinelInfo
     * @return
     */
    int addSentinelInfo(BusSentinelInfo sentinelInfo);

    /**
     * 删除在线资源
     *
     * @param id
     * @return
     */
    int removeSentinelInfo(Long id);

    /**
     * 更新在线资源
     *
     * @param sentinelInfo
     * @return
     */
    int updateSentinelInfo(BusSentinelInfo sentinelInfo);

    /**
     * 查询在线资源列表
     *
     * @param sentinelInfo
     * @return
     */
    List<BusSentinelInfo> getSentinelList(BusSentinelInfo sentinelInfo);

    /**
     * 查询所有的根资源列表
     *
     * @return
     */
    List<BusSentinelInfo> getRootSentinelList();

    /**
     * 构建在线资源树
     *
     * @return
     */
    List<BusSentinelInfo> buildSentinelTree();

    /**
     * 检查是否存在子资源
     *
     * @param id
     * @return
     */
    boolean hasChildBySentinelId(Long id);

    /**
     * 根据ID得到资源
     *
     * @param id
     * @return
     */
    BusSentinelInfo getSentinelById(Long id);


    /**
     * 查找在线资源
     *
     * @param api
     * @param local
     * @param startTime
     * @param endTime
     * @param collection
     * @param limit
     * @param next
     * @return
     */
    JSONObject search(String api, String local, Date startTime, Date endTime, String collection, Integer limit, Integer next, Integer cloud,String filter);

    /**
     * 根据数据集ID查询图层列表
     *
     * @param pid
     * @return
     */
    List<BusSentinelInfo> getLayerListByPid(Long pid);

    /**
     * WMTS切片
     * @param instanceId
     * @param layerId
     * @param format
     * @param tileMatrix
     * @param tileRow
     * @param tileCol
     * @param startTime
     * @param endTime
     */
    void tile(HttpServletRequest request, HttpServletResponse response,String id,String api,String instanceId, String layerId, String format, int tileMatrix, int tileRow, int tileCol, String startTime, String endTime) throws Exception;

    /**
     * 检查是否存在全波段图层
     * @param id
     * @return
     */
    boolean hasAllBandLayer(Long id);

    /**
     * 查询全波段的图层
     * @param id
     * @return
     */
    BusSentinelInfo getAllBandLayer(Long id);

    void downloadSentinelData(String name,String s3Url);
}
