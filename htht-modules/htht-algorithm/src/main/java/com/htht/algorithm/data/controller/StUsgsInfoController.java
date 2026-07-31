package com.htht.algorithm.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.dtflys.forest.annotation.Get;
import com.htht.algorithm.data.domain.StUsgsInfo;
import com.htht.algorithm.data.domain.vo.UsgsSreachVo;
import com.htht.algorithm.data.service.StOnlineDownloadInfoService;
import com.htht.algorithm.data.service.StUsgsInfoService;
import com.htht.algorithm.data.utils.dataDownload.sentinelDownload.SentinelDataUtils;
import com.htht.common.core.domain.R;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.List;

/**
 *  在线数据下载控制器 usgs/sentinel
 *
 * @author MSFY
 * @since 2025-08-29
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/stUsgsInfo")
public class StUsgsInfoController {

    private final StUsgsInfoService stUsgsInfoService;



    @PostMapping
    public R<Void> addUsgsInfo(@RequestBody StUsgsInfo stUsgsInfo) {
        return stUsgsInfoService.add(stUsgsInfo)?R.ok():R.fail();
    }
    @PutMapping
    public R<Void> updateUsgsInfo(@RequestBody StUsgsInfo stUsgsInfo) {
        return stUsgsInfoService.updateById(stUsgsInfo)?R.ok():R.fail();
    }
    @DeleteMapping("/{id}")
    public R<Void> deleteUsgsInfo(@PathVariable Long id) {
        return stUsgsInfoService.removeById(id)?R.ok():R.fail();
    }
    @GetMapping("/{id}")
    public R<StUsgsInfo> getUsgsInfo(@PathVariable Long id) {
        return R.ok();
    }
    @GetMapping
    public R<List<StUsgsInfo>> getUsgsInfoList() {
        return R.ok(stUsgsInfoService.list());
    }

    @GetMapping("/tree")
    public R<List<StUsgsInfo>> getUsgsInfoTree() {
        return R.ok(stUsgsInfoService.buildUsgsInfoTree());
    }

    /**
     * 搜索USGS数据接口
     */
    @PostMapping("/search")
    public R<String> search(
        @Validated @RequestBody UsgsSreachVo usgsSreachVo
    ){
        try {
            if(usgsSreachVo.getOnlineType()==1){
                return R.ok("查询结果",stUsgsInfoService.search(usgsSreachVo));
            }else{
                    if(usgsSreachVo.getSentinel2Types().isEmpty()){
                        return R.fail("请输入WTK和Sentinel2类型");
                    }

                    return R.ok("查询结果",SentinelDataUtils.searchSentinelDataWithPagination(
                        "SENTINEL-2",
                        usgsSreachVo.getSentinel2Types(),
                        usgsSreachVo.getStartDate(),
                        usgsSreachVo.getEndDate(),
                        usgsSreachVo.getWtk(),
                        usgsSreachVo.getPageNum(), usgsSreachVo.getPageSize(), true
                    ).toString());
            }
        } catch (IOException e) {
            throw new GlobalException(e.getMessage());
        }

    };


    /**
     * 下载Landsat数据接口
     */
    @SaCheckPermission("data:download:online")
    @GetMapping(value = "/download/landsat")
    public R<Void> downloadLandsatData(String dataName,String datasetId, String entityId) {
        return stUsgsInfoService.downloadLandsatData(dataName,datasetId,entityId)?R.ok():R.fail();
    }

    /**
     * 下载sentinel数据接口
     */
    @SaCheckPermission("data:download:online")
    @GetMapping(value = "/download/sentinel")
    public R<Void> downloadSentinelData(String dataName,String downloadUrl) {
        stUsgsInfoService.downloadSentinelData(dataName,downloadUrl);
        return R.ok();
    }

    /**
     * 暂停下载
     */
    @SaCheckPermission("data:download:online")
    @GetMapping(value = "/pause/{id}")
    public R<Void> pauseDownload(@PathVariable Long id) {
        return stUsgsInfoService.pauseDownload(id)?R.ok():R.fail();
    }

}
