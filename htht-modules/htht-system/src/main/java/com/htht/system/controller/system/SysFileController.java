package com.htht.system.controller.system;

import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 本地文件上传
 * @author liaozhenshan
 * @version 1.0
 * @date 2023/4/4 10:07
 */
@RequestMapping(value = "/system/file")
@RestController
@RequiredArgsConstructor
public class SysFileController extends BaseController {

    private final ISysFileService iSysFileService;

    /**
     * 上传文件
     * @param file
     * @return
     */
    @PostMapping("/uploadStatic")
    public R staticFile(@RequestPart("file") MultipartFile file) {
        SysFile sysFile = iSysFileService.upload(file);
        return R.ok(sysFile);
    }

    /**
     * 根据文件id下载文件
     * @param response
     * @param id
     * @throws IOException
     */
    @GetMapping("/download/{id}")
    public void download(HttpServletResponse response,@PathVariable Long id) throws IOException {
        iSysFileService.download(response,id,"随便打，没效果的");
    }

    /**
     * 根据文件id查看文件详情
     * @param id
     * @throws IOException
     */
    @GetMapping("/{id}")
    public R fileDetail(@PathVariable("id")Long id){
        return R.ok(iSysFileService.getById(id));
    }

    /**
     * 根据文件id删除文件
     * @param id
     * @throws IOException
     */
    @DeleteMapping ("/delete/{id}")
    public R<Void> delete(@PathVariable Long id){
        return toAjax(iSysFileService.delete(id));
    }

}
