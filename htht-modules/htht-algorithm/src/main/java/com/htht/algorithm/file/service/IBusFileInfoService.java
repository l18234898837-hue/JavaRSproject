package com.htht.algorithm.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.file.domain.BusFileInfo;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
public interface IBusFileInfoService extends IService<BusFileInfo> {

    /**
     * 文件上传
     *
     * @param file
     * @return
     */
    BusFileInfo uploadFile(MultipartFile file, String uploadPath) throws IOException;

    /**
     * 删除文件
     *
     * @param id
     * @return
     */
    int removeFile(Long id);

    /**
     * 根据文件ID得到文件信息
     *
     * @param id
     * @return
     */
    BusFileInfo getFileInfoById(Long id);

    /**
     * 文件扫描
     *
     * @param file
     * @param folderPath
     * @param
     * @return
     */
    BusFileInfo scan(File file, String folderPath);

    /**
     * 文件下载
     *
     * @param response
     * @param id
     * @throws IOException
     */
    void download(HttpServletResponse response, Long id,String UploadPath) throws IOException;

    void download(HttpServletResponse response, String filePath,String uploadPath,String fileName);

    void downloadThumbJpg(HttpServletResponse response, String filePath ,String uploadPath) throws IOException;
}

