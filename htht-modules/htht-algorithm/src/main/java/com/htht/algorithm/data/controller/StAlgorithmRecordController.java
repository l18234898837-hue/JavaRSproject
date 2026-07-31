package com.htht.algorithm.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StAlgorithmParamInfo;
import com.htht.algorithm.data.domain.StAlgorithmRecord;
import com.htht.algorithm.data.domain.vo.AddAlgorithmTaskVo;
import com.htht.algorithm.data.domain.vo.SubmitPublicVo;
import com.htht.algorithm.data.domain.vo.SubmitUserVo;
import com.htht.algorithm.data.service.StAlgorithmParamInfoService;
import com.htht.algorithm.data.service.StAlgorithmRecordCatalogInfoService;
import com.htht.algorithm.data.service.StAlgorithmRecordService;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.common.core.domain.R;
import com.htht.common.helper.LoginHelper;
import com.htht.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 算法运行记录表，用于追踪每次算法执行的状态、用户、日志及运行目录 前端控制器
 *
 * @author MSFY
 * @since 2025-09-18
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/stAlgorithmRecord")
public class StAlgorithmRecordController {
    private final StAlgorithmRecordService stAlgorithmRecordService;
    private final StAlgorithmParamInfoService stAlgorithmParamInfoService;

    @SaCheckPermission("data:calculate:LandsatAndSentinel2")
    @PostMapping("/addSetting")
    public R<StAlgorithmParamInfo> addSetting(@RequestPart("file") MultipartFile file){
        String onlineFileExtension = FileStrUtil.getOnlineFileExtension(file);
        if(StringUtils.isBlank(onlineFileExtension)){
            return R.fail("请上传文件");
        }
        if(onlineFileExtension.equals("txt")){
            stAlgorithmRecordService.addSetting(file);
            R.ok(stAlgorithmParamInfoService.add(file,1));
        }else if(onlineFileExtension.equals("shp")){
            stAlgorithmRecordService.addSetting(file);
            R.ok(stAlgorithmParamInfoService.add(file,2));
        }
        return R.fail("请上传系统支持的文件");
    }
    @SaCheckPermission("data:calculate:LandsatAndSentinel2")
    @PostMapping()
    public R<Void> add(@RequestBody @Validated AddAlgorithmTaskVo aATV){
        stAlgorithmRecordService.add(aATV);
        return R.ok();
    }

    //获取用户运行结果
    @GetMapping()
    public R<StAlgorithmRecord> getUserResult(){
        return R.ok(stAlgorithmRecordService.getUserResult(LoginHelper.getUserId()));
    }

    @GetMapping("/catalog")
    public Map<String, Map<String, List<BusDataInfo>>> getValueByAlgorithmId(){
        return stAlgorithmRecordService.getVoByAlgorithmId();
    }

    /**
     * 提交数据到个人数据集
     */
    @PostMapping("/submit/user")
    public R<Void> submitUser(@RequestBody @Validated SubmitUserVo suv){
        stAlgorithmRecordService.submitUser(suv);
        return R.ok();
    }

    /**
     * 提交数据到公共数据集
     */
    @PostMapping("/submit/public")
    public R<Void> submitPublic(@RequestBody @Validated SubmitPublicVo spv){
        stAlgorithmRecordService.submitPublic(spv);
        return R.ok();
    }


}
