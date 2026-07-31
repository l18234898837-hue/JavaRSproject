package com.htht.algorithm.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.algorithm.data.domain.StOnlineDownloadRecord;
import com.htht.algorithm.data.service.StOnlineDownloadRecordService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * 在线下载数据表展示
 *
 * Author: MSFY
 * Description: TODO 在线下载数据表展示
 * DateTime: 2026/1/16 17:47
 **/

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/StOnlineDownloadRecord")
public class StOnlineDownloadRecordController {
    private final StOnlineDownloadRecordService stOnlineDownloadRecordService;

    /**
     * 继续下载
     */
    @SaCheckPermission("data:download:online")
    @GetMapping(value = "/continue/{id}")
    public R<Void> continueDownload(@PathVariable Long id) {
        stOnlineDownloadRecordService.updateStatus(id,1);
        stOnlineDownloadRecordService.checkDownload();
        return R.ok();
    }

    /**
     * 暂停下载
     */
    @SaCheckPermission("data:download:online")
    @GetMapping(value = "/pause/{id}")
    public R<Void> pauseDownload(@PathVariable Long id) {
        return stOnlineDownloadRecordService.pauseDownload(id)?R.ok():R.fail();
    }

    /**
     * 删除下载记录
     */
    @SaCheckPermission("data:download:online")
    @GetMapping(value = "/delete/{id}")
    public R<Void> deleteDownloadRecord(@PathVariable Long id) {
        return stOnlineDownloadRecordService.removeById(id)?R.ok():R.fail();
    }
    /**
     * 获取下载记录
     */
    @GetMapping()
    public R<TableDataInfo<StOnlineDownloadRecord>> getDownloadRecord(Long userId, PageQuery pageQuery) {
        return R.ok(stOnlineDownloadRecordService.getListByUserId(userId,pageQuery));
    }
}
