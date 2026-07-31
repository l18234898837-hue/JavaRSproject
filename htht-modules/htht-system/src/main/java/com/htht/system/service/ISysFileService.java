package com.htht.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.system.domain.SysFile;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
* @author lzs
* @description 针对表【upload_file】的数据库操作Service
* @createDate 2023-04-04 10:06:37
*/
public interface ISysFileService extends IService<SysFile> {

    SysFile upload(MultipartFile file);

    void download(HttpServletResponse response, Long id) throws IOException;

    boolean delete(Long id);

    public void download(HttpServletResponse response, Long id,String uploadPath) throws IOException;
}
