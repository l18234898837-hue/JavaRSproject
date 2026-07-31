package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.StDownloadRecord;
import com.htht.algorithm.data.service.StDownloadRecordService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * 数据下载记录表，记录用户提交的下载任务信息 前端控制器
 *弃用了，项目找不到引用了
 * @author MSFY
 * @since 2025-08-11
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stDownloadRecord")
public class StDownloadRecordController {

    private final StDownloadRecordService stDownloadRecordService;

    /**
     * 查看数据下载记录
     * @return
     */
    @GetMapping("/list")
    public R<TableDataInfo<StDownloadRecord>> list(StDownloadRecord stDownloadRecord, PageQuery pageQuery)
    {
        return R.ok(stDownloadRecordService.listPage( stDownloadRecord, pageQuery));
    }

    /**
     * 根据id删除下载记录
     */
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id)
    {
        return R.ok(stDownloadRecordService.removeById(id));
    }

}
