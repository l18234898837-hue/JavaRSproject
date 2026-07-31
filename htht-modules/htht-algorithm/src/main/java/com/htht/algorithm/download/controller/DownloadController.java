package com.htht.algorithm.download.controller;

import com.htht.algorithm.data.utils.FileTreeUtil;
import com.htht.algorithm.download.domain.vo.FileTreeVO;
import com.htht.algorithm.download.util.DownloadUtil;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import java.io.IOException;

/**
 *
 * 跳过nginx的下载包，直接强制性下载，目前没有安全功能兼容
 *
 * Author: MSFY
 * Description:
 * DateTime: 2026/7/20 15:50
 **/
@RestController
@RequestMapping("/download")
@Validated
@RequiredArgsConstructor
public class DownloadController {
    /**
     * 下载接口，不通过nginx下载
     */
    @GetMapping("/download")
    public void download(@NotBlank(message = "文件路径不能为空") String filePath, HttpServletResponse response) {
        try {
            DownloadUtil.download(response,filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 测试接口，获取本机目录，平时关着
     */
//    @GetMapping("/scanFolder")
//    public R<FileTreeVO> scanFolder(@NotBlank(message="文件夹路径不能为空") String filePath){
//        try{
//            return R.ok(FileTreeUtil.buildFileTree(filePath));
//        }catch (Exception e){
//            return R.fail(e.getMessage());
//        }
//    }
}
