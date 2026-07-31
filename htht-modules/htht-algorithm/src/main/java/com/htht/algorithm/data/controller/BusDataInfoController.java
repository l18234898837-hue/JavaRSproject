package com.htht.algorithm.data.controller;


import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.htht.algorithm.data.domain.vo.BusDataInfoDownloadVo;
import com.htht.algorithm.base.event.LogBaseEvent;
import com.htht.algorithm.data.event.StDownloadLogEvent;
import com.htht.algorithm.data.service.impl.BusDataInfoServiceImpl;
import com.htht.common.annotation.Log;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.BusinessType;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.dto.TransformDTO;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.StringUtils;
import com.htht.system.util.recordUserOperateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * 数据资源管理
 *
 * @author wjx
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/algorithm/data")
public class BusDataInfoController extends BaseController {

    private final IBusDataInfoService dataInfoService;
    private final IBusFileInfoService fileInfoService;
    private final BusDataInfoServiceImpl busDataInfoServiceImpl;



    /**
     * 新增数据资源
     */
    @SaCheckPermission("algorithm:data:add")
    @Log(title = "数据资源管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BusDataInfo dataInfo) {
        if (!dataInfoService.checkNameUnique(dataInfo)) {
            return R.fail("新增数据资源'" + dataInfo.getName() + "'失败，数据资源已存在");
        }
        return toAjax(dataInfoService.insertDataInfo(dataInfo));
    }

    /**
     * 删除数据资源
     *
     * @param id 数据资源id
     */
    @SaCheckPermission(value={"algorithm:data:remove","data:remove:system"},mode= SaMode.OR)
    @Log(title = "资源数据管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        return toAjax(dataInfoService.removeDataInfo(id));
    }

    /**
     * 修改数据资源
     */
    @SaCheckPermission("algorithm:data:edit")
    @Log(title = "数据资源管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BusDataInfo dataInfo) {
        if (!dataInfoService.checkNameUnique(dataInfo)) {
            return R.fail("修改数据资源'" + dataInfo.getName() + "'失败，数据资源已存在");
        }
        dataInfoService.updateDataInfo(dataInfo);
        return R.ok();
    }

    /**
     * 分页查询数据资源
     */
    @GetMapping("/page")
    public TableDataInfo<BusDataInfo> list(BusDataInfo dataInfo, PageQuery pageQuery) {
        return dataInfoService.selectPage(dataInfo, pageQuery);
    }
    /**
     * 项目扩展类型文件上传
     */
    @SaCheckPermission("algorithm:data:upload")
    @Log(title = "数据资源管理", businessType = BusinessType.UPLOAD)
    @PostMapping(value = "/uploadOther", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> uploadOther(@RequestPart("file") MultipartFile file) throws IOException {
        return R.ok(dataInfoService.uploadOtherFile(file));
    }

    /**
     * GeoTiff文件上传
     *
     * @param file
     * @return
     * @throws IOException
     */
    @SaCheckPermission("algorithm:data:upload")
    @Log(title = "数据资源管理", businessType = BusinessType.UPLOAD)
    @PostMapping(value = "/uploadGrid", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> uploadGrid(@RequestPart("file") MultipartFile file) throws IOException {
        return R.ok(dataInfoService.uploadGridFile(file));
    }

    /**
     * shp文件上传
     *
     * @param file
     * @return
     * @throws IOException
     */
    @Log(title = "数据资源管理", businessType = BusinessType.UPLOAD)
    @SaCheckPermission("algorithm:data:upload")
    @PostMapping(value = "/uploadShp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> uploadShp(@RequestPart("file") MultipartFile file) throws IOException {
        return R.ok(dataInfoService.uploadShpFile(file));
    }

    /**
     * 根据文件ID查询文件信息
     */
    @GetMapping("/file/{fileId}")
    public R<BusFileInfo> getInfoByFileId(@PathVariable Long fileId) {
        return R.ok(fileInfoService.getFileInfoById(fileId));
    }

    /**
     * 数据资源入库
     *
     * @param id
     * @return
     * @throws IOException
     */
    @SaCheckPermission("algorithm:data:storage")
    @Log(title = "数据资源管理", businessType = BusinessType.UPDATE)
    @PutMapping(value = "/storage/{id}")
    public R<Void> storage(@PathVariable(value = "id") Long id) throws IOException {
        dataInfoService.storage(id);
        return R.ok();
    }

    /**
     * 一键入库
     */
    @SaCheckPermission("algorithm:data:storage")
    @PutMapping(value = "/storageAll")
    public R<Void> storageAll() {
        dataInfoService.storageAll();
        return R.ok();
    }


    /**
     * 根据id查询数据资源详情
     *
     * @param id
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/{id}")
    public R<BusDataInfo> queryById(@PathVariable(value = "id") Long id) {
        return R.ok(dataInfoService.getDataInfoById(id));
    }
    /**
     * 根据id下载数据资源，压缩包返回
     */
    @PostMapping("/download")
    public void download(@RequestBody BusDataInfoDownloadVo dataInfoDownloadVo, HttpServletResponse response){
        dataInfoService.checkDownloadPre();
        List<StDownloadLogEvent> downloadLogEvents = busDataInfoServiceImpl.checkDownloadPermission(dataInfoDownloadVo.getDataIds());
        try {
            busDataInfoServiceImpl.download(downloadLogEvents,dataInfoDownloadVo,response);
            if(dataInfoDownloadVo.getType()==1){
                recordUserOperateUtil.recordUserOperateLogin("数据服务-遥感产品","下载",getDownloadLogStr(downloadLogEvents),"成功");
            }else if(dataInfoDownloadVo.getType()==2){
                recordUserOperateUtil.recordUserOperateLogin("遥感应用-遥感查询","下载",getDownloadLogStr(downloadLogEvents),"成功");
            }
        } catch (Exception e) {
            downloadLogEvents.forEach(downloadLogEvent -> downloadLogEvent.setDownloadStatus(Short.valueOf("2")));
            if(dataInfoDownloadVo.getType()==1){
                recordUserOperateUtil.recordUserOperateLogin("数据服务-遥感产品","下载",e.getMessage(),"失败");
            }else if(dataInfoDownloadVo.getType()==2){
                recordUserOperateUtil.recordUserOperateLogin("遥感应用-遥感查询","下载",e.getMessage(),"失败");
            }
            downloadComplete();
            throw new GlobalException(e.getMessage());
        }finally {
            downloadLogEvents.forEach(LogBaseEvent::sendEvent);
        }
    }

    private String getDownloadLogStr(List<StDownloadLogEvent> downloadLogEvents){
        Set<String> dataLevelSet=new HashSet<>();
        for (StDownloadLogEvent downloadLogEvent : downloadLogEvents) {
            dataLevelSet.add(downloadLogEvent.getDataLevel());
        }
        return  "数量 ："+downloadLogEvents.size()+"; 数据级别："+ StringUtils.join(dataLevelSet,",")+"; 下载地址：本地";
    }



    /**
     * 数据下载完成，清除数据下载状态
     */
    @GetMapping("/download/complete")
    public R<Void> downloadComplete() {
        dataInfoService.clearDownloadStatus();
        return R.ok();
    }

    /**
     * 根据id返回对应文件的缩略图
     */
    @GetMapping("/ThumbJpg/{id}")
    public void getThumbJpg(@PathVariable(value = "id") Long id, HttpServletResponse response) throws IOException {
        dataInfoService.downloadThumbJpg(response, id);
    }

    /**
     * 根据id以及色带生成新的缩略图返回对应文件
     */
    @GetMapping("/ThumbJpgByColorRampId/{id}")
    public void getThumbJpg(@PathVariable(value = "id") Long id,@RequestParam(value = "colorRampId")Long colorRampId, HttpServletResponse response) throws IOException {
        dataInfoService.downloadThumbJpg(response, id, colorRampId);
    }



    /**
     * 把shp数据直接变成geoJson然后返回给前端
     */
    @GetMapping("/shp/{id}")
    public R<String> getShpGeoJson(@PathVariable(value = "id") Long id) {
        BusDataInfo dataInfoById = busDataInfoServiceImpl.getDataInfoById(id);
        if (dataInfoById.getType()!=2) {
            return R.fail("数据资源不是shp文件");
        }
        return R.ok("操作成功",dataInfoService.getShpGeoJson(id));
    }



    /**
     * 根据查询条件检索资源
     *
     * @param params
     * @return
     */
    @PostMapping(value = "/condition/page")
    public TableDataInfo<BusDataInfo> getDataInfoListByCondition(@RequestBody JSONObject params) {
        return getDataInfoList(params,1);
    }


    /**
     * 根据查询条件检索用户目录资源
     *
     * @param params
     * @return
     */
    @PostMapping(value = "/condition/user/page")
    public TableDataInfo<BusDataInfo> getDataInfoListByConditionAndUser(@RequestBody JSONObject params) {
        return getDataInfoList(params,2);
    }


    /**
     * 根据查询条件检索所有资源（资源目录+用户目录）
     *
     * @param params
     * @return
     */
    @PostMapping(value = "/condition/all/page")
    public TableDataInfo<BusDataInfo> getDataInfoListByConditionAndAll(@RequestBody JSONObject params) {
        return getDataInfoList(params,3);
    }

    private TableDataInfo<BusDataInfo>  getDataInfoList(JSONObject params,int type){
        JSONObject bo = params.getJSONObject("bo");
        Integer logType = params.getInteger("type");

        try {
            // 处理datasets数组：String类型数组 -> Long类型列表
            JSONArray datasetStrArray = bo.getJSONArray("datasets");
            List<Long> datasetLongList = new ArrayList<>();
            for (int i = 0; i < datasetStrArray.size(); i++) {
                // 逐个将字符串元素转换为Long
                String datasetStr = datasetStrArray.getString(i);
                datasetLongList.add(Long.valueOf(datasetStr));
            }
            // 将转换后的Long列表放回bo中（覆盖原字符串数组）
            bo.put("datasets", datasetLongList);
            if(type==2||type==3){
                bo.put("userId", Long.valueOf(bo.getString("userId")));
            }
            PageQuery pageQuery = params.getObject("page", PageQuery.class);
            TableDataInfo<BusDataInfo> returnValue=null;
            if(type==1){
                returnValue= dataInfoService.getDataInfoListByCondition(bo, pageQuery);
            } else if (type==2) {
                returnValue= dataInfoService.getDataInfoListByConditionAndUser(bo, pageQuery);
            }else if(type==3){
                returnValue= dataInfoService.getDataInfoListByConditionAndAll(bo, pageQuery);
            }

            if(logType!=null){
                if(logType==1){
                    recordUserOperateUtil.recordUserOperateLogin("数据服务-遥感产品","查询","","成功");
                }else if(logType==2){
                    recordUserOperateUtil.recordUserOperateLogin("遥感应用-遥感查询","查询","","成功");
                }
            }
            if (returnValue != null) {
                        dataInfoService.injectValueSatelliteValue(returnValue.getRows());
            }
            return returnValue;
        } catch (Exception e) {
            if(logType!=null){
                if(logType==1){
                    recordUserOperateUtil.recordUserOperateLogin("数据服务-遥感产品","查询",e.getMessage(),"失败");
                }else if(logType==2){
                    recordUserOperateUtil.recordUserOperateLogin("遥感应用-遥感查询","查询",e.getMessage(),"失败");
                }
            }
            throw new GlobalException(e.getMessage());
        }
    }


    /**
     * 检查两资源是否相交，相交则返回true
     *
     * @param id1
     * @param id2
     * @return
     */
    @GetMapping(value = "/check/intersects")
    public R<Boolean> checkDataIntersects(Long id1, Long id2) {
        return R.ok(dataInfoService.checkDataIntersects(id1, id2));
    }

    /**
     * 统计
     *
     * @param id
     * @param statsField
     * @param groupField
     * @param oneField
     * @param twoField
     * @return
     */
    @GetMapping(value = "/stats")
    public R<List<Map<String, Object>>> statistic(
        @RequestParam(value = "id", required = true) Long id,
        @RequestParam(value = "statsField", required = true) String statsField,
        @RequestParam(value = "groupField", required = true) String groupField,
        @RequestParam(value = "oneField", required = true) String oneField,
        @RequestParam(value = "twoField", required = false) String twoField
    ) {
        return R.ok(dataInfoService.statistic(id, statsField, groupField, oneField, twoField));
    }

    /**
     * 将坐标数组按指定坐标系进行转换
     *
     * @param dto
     * @return
     */
    @PostMapping(value = "/transform")
    public R<double[][]> transformCoordinates(@RequestBody TransformDTO dto) {
        return R.ok(dataInfoService.transformCoordinates(dto));
    }

    /**
     * 扫描入库
     *
     * @return
     * @throws IOException
     */
    @SaCheckPermission("algorithm:data:scan")
    @PostMapping(value = "/scan")
    public R<Void> scan() {
        dataInfoService.scan();
        return R.ok();
    }

    /**
     * 导出资源列表
     */
    @Log(title = "资源管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BusDataInfo dataInfo, HttpServletResponse response) {
        dataInfoService.export(dataInfo, response);
    }


}
