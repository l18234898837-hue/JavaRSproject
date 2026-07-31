package com.htht.business.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.htht.business.domain.StPythonAlgorithmUserStream;
import com.htht.business.service.StPythonAlgorithmUserStreamService;
import com.htht.common.core.domain.R;
import com.htht.common.utils.StringUtils;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 用户、执行流程与数据集关联表，含名称和数字编码的执行状态，用于查询执行后的流程信息 前端控制器
 *
 * @author MSFY
 * @since 2025-08-15
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stPythonAlgorithmUserStream")
public class StPythonAlgorithmUserStreamController {
    private final StPythonAlgorithmUserStreamService stPythonAlgorithmUserStreamService;
    private final ISysFileService iSysFileService;
    @GetMapping("/list")
    public R<List<StPythonAlgorithmUserStream>> list(StPythonAlgorithmUserStream stPythonAlgorithmUserStream) {
        return R.ok(
            stPythonAlgorithmUserStreamService.list(new LambdaQueryWrapper<StPythonAlgorithmUserStream>()
                .like(StringUtils.isNotBlank(stPythonAlgorithmUserStream.getName()),StPythonAlgorithmUserStream::getName,stPythonAlgorithmUserStream.getName())
                .eq(ObjectUtils.isNotEmpty(stPythonAlgorithmUserStream.getUserId()),StPythonAlgorithmUserStream::getUserId,stPythonAlgorithmUserStream.getUserId())
                .eq(ObjectUtils.isNotNull(stPythonAlgorithmUserStream.getPythonStreamId()),StPythonAlgorithmUserStream::getPythonStreamId,stPythonAlgorithmUserStream.getPythonStreamId())
                .eq(ObjectUtils.isNotNull(stPythonAlgorithmUserStream.getExecutionStatus()),StPythonAlgorithmUserStream::getExecutionStatus,stPythonAlgorithmUserStream.getExecutionStatus())
            ));
    }

    @GetMapping ("dataset/{id}")
    public void dataset(@PathVariable Long id, HttpServletResponse response) throws IOException {
        StPythonAlgorithmUserStream algorithmUserStreamServiceById = stPythonAlgorithmUserStreamService.getById(id);
        iSysFileService.download(response,algorithmUserStreamServiceById.getDatasetId(),"123");
    }

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        return R.ok(stPythonAlgorithmUserStreamService.removeById(id)?"删除成功":"删除失败");
    }
}
