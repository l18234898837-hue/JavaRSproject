package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.vo.StSubmitAuditRecordVo;
import com.htht.algorithm.data.service.StSubmitAuditRecordService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

/**
 * 提交数据审核表 前端控制器
 *
 * @author MSFY
 * @since 2025-09-30
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stSubmitAuditRecord")
public class StSubmitAuditRecordController {
    private final StSubmitAuditRecordService stSubmitAuditRecordService;

    @DeleteMapping ("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        boolean b = stSubmitAuditRecordService.delectBy(id);
        return b ? R.ok() : R.fail();
    }

    @GetMapping()
    public R<TableDataInfo<StSubmitAuditRecordVo>> page(Long status, String nameKey, PageQuery pageQuery){
       return R.ok(stSubmitAuditRecordService.page( status,  nameKey, pageQuery));
    }

    @PostMapping("/{recordId}/{status}")
    public R<Void> audit(@PathVariable Long recordId, @PathVariable Integer status){
        stSubmitAuditRecordService.audit(recordId, status);
        return R.ok();
    }

    @PostMapping("/{recordId}/{dataId}/{status}")
    public R<Void> auditData(@PathVariable Long recordId, @PathVariable Long dataId, @PathVariable Integer status){
        stSubmitAuditRecordService.auditData(recordId, dataId, status);
        return R.ok();
    }

}
