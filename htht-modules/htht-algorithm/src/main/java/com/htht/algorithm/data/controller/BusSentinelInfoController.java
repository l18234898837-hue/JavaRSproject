package com.htht.algorithm.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import com.alibaba.fastjson.JSONObject;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.algorithm.data.domain.BusSentinelInfo;
import com.htht.algorithm.data.service.IBusSentinelInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

/**
 * 在线资源管理
 *
 * @author wjx
 * @since 2023-05-25
 */
@RestController
@RequestMapping("/algorithm/sentinel")
@Validated
@RequiredArgsConstructor
public class BusSentinelInfoController extends BaseController {

    private final IBusSentinelInfoService sentinelInfoService;

    /**
     * 新增在线资源
     *
     * @param sentinelInfo
     * @return
     */
    @PostMapping
    public R<Void> addSentinel(@Validated @RequestBody BusSentinelInfo sentinelInfo) {
        return toAjax(sentinelInfoService.addSentinelInfo(sentinelInfo));
    }

    /**
     * 删除在线资源
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public R<Void> removeMenu(@PathVariable(value = "id") Long id) {
        if (sentinelInfoService.hasChildBySentinelId(id)) {
            return R.fail("存在子资源，不允许删除");
        }
        return toAjax(sentinelInfoService.removeSentinelInfo(id));
    }

    /**
     * 更改在线资源
     *
     * @param sentinelInfo
     * @return
     */
    @PutMapping
    public R<Void> updateSentinel(@Validated @RequestBody BusSentinelInfo sentinelInfo) {
        if (sentinelInfo.getId().equals(sentinelInfo.getPid())) {
            return R.fail("修改资源'" + sentinelInfo.getName() + "'失败，上级资源不能选择自己");
        }
        return toAjax(sentinelInfoService.updateSentinelInfo(sentinelInfo));
    }

    /**
     * 查询在线资源列表
     *
     * @param sentinelInfo
     * @return
     */
    @GetMapping(value = "/list")
    public R<List<BusSentinelInfo>> list(BusSentinelInfo sentinelInfo) {
        return R.ok(sentinelInfoService.getSentinelList(sentinelInfo));
    }

    /**
     * 查询在线根资源列表
     *
     * @return
     */
    @GetMapping(value = "/root/list")
    public R<List<BusSentinelInfo>> rootList() {
        return R.ok(sentinelInfoService.getRootSentinelList());
    }

    /**
     * 根据资源ID得到资源信息
     *
     * @param id
     * @return
     */
    @GetMapping(value = "/{id}")
    public R<BusSentinelInfo> getMenuById(@PathVariable Long id) {
        return R.ok(sentinelInfoService.getSentinelById(id));
    }

    /**
     * 构建资源树
     *
     * @return
     */
    @GetMapping(value = "/tree")
    public R<List<BusSentinelInfo>> buildTree() {
        return R.ok(sentinelInfoService.buildSentinelTree());
    }

    /**
     * 在线查询数据
     *
     * @return
     */
    @PostMapping(value = "/search")
    public R<JSONObject> search(
        String api,
        String local,
        @DateTimeFormat(pattern = "yyyy-MM-dd") Date startTime,
        @DateTimeFormat(pattern = "yyyy-MM-dd") Date endTime,
        String collection,
        Integer limit,
        Integer next,
        @RequestParam(required = false) Integer cloudCoverage,
        @RequestParam(required = false) String filter
    ) {
        JSONObject object = sentinelInfoService.search(api, local, startTime, endTime, collection, limit, next, cloudCoverage, filter);
        return R.ok(object);
    }

    /**
     * 根据父级ID查询图层列表
     *
     * @param pid 父级资源ID
     * @return 图层列表的响应结果
     */
    @GetMapping(value = "/layers/{pid}")
    public R<List<BusSentinelInfo>> getLayerListByPid(@PathVariable(value = "pid") Long pid) {
        return R.ok(sentinelInfoService.getLayerListByPid(pid));
    }

    /**
     * WMTS切片服务接口
     * 该接口用于处理WMTS切片请求，返回对应的瓦片数据
     *
     * @param request       HTTP请求对象
     * @param response      HTTP响应对象
     * @param id            资源ID
     * @param api           API接口前缀
     * @param instanceId    实例ID
     * @param layerId       图层ID
     * @param format        切片格式，默认为png
     * @param tileMatrix    切片矩阵等级
     * @param tileRow       切片行号
     * @param tileCol       切片列号
     * @param startTime     时间范围起始
     * @param endTime       时间范围结束
     */
    @GetMapping(value = "/wmts")
    @SaIgnore
    public void tile(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam(required = true) String id,
        @RequestParam(required = true) String api,
        @RequestParam(required = true) String instanceId,
        @RequestParam(required = true) String layerId,
        @RequestParam(required = false, defaultValue = "png") String format,
        @RequestParam(required = true) Integer tileMatrix,
        @RequestParam(required = true) Integer tileRow,
        @RequestParam(required = true) Integer tileCol,
        @RequestParam(required = true) String startTime,
        @RequestParam(required = true) String endTime
    ) {
        try {
            sentinelInfoService.tile(request, response, id, api, instanceId, layerId, format, tileMatrix, tileRow, tileCol, startTime, endTime);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 检查是否存在全波段的实例和图层
     * @param id
     * @return
     */
    @GetMapping(value = "/allband")
    public R<Boolean> hasAllBandLayer(Long id){
        return R.ok(sentinelInfoService.hasAllBandLayer(id));
    }

    /**
     * 通过传入s3链接下载sentinel数据
     */

    @SaCheckPermission("data:download:online")
    @GetMapping(value = "/download/sentinel")
    public R<Void> downloadSentinelData(String name,String s3Url){
        if(name == null || name.isEmpty()){
            return R.fail("请传入在线文件名");
        }
        if(s3Url == null || s3Url.isEmpty()){
            return R.fail("请传入s3链接");
        }
        sentinelInfoService.downloadSentinelData(name,s3Url);
        return R.ok();
    }
}
