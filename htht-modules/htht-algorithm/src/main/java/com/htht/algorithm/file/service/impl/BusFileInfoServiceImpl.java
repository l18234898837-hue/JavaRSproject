package com.htht.algorithm.file.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.exception.GlobalException;
import com.htht.common.exception.ServiceException;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.mapper.BusFileInfoMapper;
import com.htht.algorithm.file.service.IBusFileInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
@Service
@RequiredArgsConstructor
public class BusFileInfoServiceImpl extends ServiceImpl<BusFileInfoMapper, BusFileInfo> implements IBusFileInfoService {

//    private final FileProperties fileProperties;

    /**
     * 文件上传
     *
     * @param file
     * @return
     */
    @Override
    public BusFileInfo uploadFile(MultipartFile file, String uploadPath) throws IOException {

        BusFileInfo fileInfo = new BusFileInfo();
        Long uid = IdUtil.getSnowflakeNextId();
        fileInfo.setId(uid);
        fileInfo.setSize(file.getSize());
        fileInfo.setFilename(file.getOriginalFilename());
        fileInfo.setExtension(FileUtils.getSuffix(file.getOriginalFilename()));
        fileInfo.setMimeType(file.getContentType());
        Path path = Path.of(uploadPath, uid + "." + fileInfo.getExtension());
        FileUtils.mkParentDirs(path);
        file.transferTo(path);
        fileInfo.setPath(path.toString());
        this.save(fileInfo);
        return fileInfo;
    }

    @Override
    public int removeFile(Long id) {
        BusFileInfo fileInfo = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("文件不存在：" + id));
        File file = new File(fileInfo.getPath());
        file.deleteOnExit();
        return this.baseMapper.deleteById(id);
    }

    @Override
    public BusFileInfo getFileInfoById(Long id) {
        return Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("数据资源文件不存在"));
    }

    @Override
    public BusFileInfo scan(File file, String folderPath) {
        String fileName = file.getName();
        String extension = FileUtils.getSuffix(fileName);

        Long size = file.getTotalSpace();
        BusFileInfo fileInfo = new BusFileInfo();
        fileInfo.setFilename(FileUtils.getPrefix(fileName));
        fileInfo.setExtension(extension);
        fileInfo.setSize(size);

        File folderFile = new File(folderPath);
        if (!folderFile.exists()) {
            folderFile.mkdirs();
        }
        Long uid = IdUtil.getSnowflakeNextId();
        fileInfo.setId(uid);
        String newFileName = uid + "." + extension;
        String filePath = folderPath + "/" + newFileName;
        FileUtils.copyFile(file, new File(filePath));

        fileInfo.setPath(filePath);
        fileInfo.setMimeType(fileName.endsWith(".tif") ? "image/tiff" : "application/zip");
        this.save(fileInfo);
        return fileInfo;
    }

//    @Override
//    public void download(HttpServletResponse response, Long id,String uploadPath) throws IOException {
//        // 读到流中
//         BusFileInfo file = Optional.ofNullable(this.getById(id))
//            .orElseThrow(() -> new ServiceException("文件不存在"));
//
//        //从 SysFile 对象获取文件绝对路径
//        String filePath = file.getPath();
//
//        if (filePath.startsWith(uploadPath)) {
//            // 读到流中
//            InputStream inputStream = new FileInputStream(filePath);// 文件的存放路径
//            try {
//                response.reset();
//                response.setContentType("application/octet-stream");
//                String filename = file.getFilename() + "." + file.getExtension();
//                response.addHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, "UTF-8"));
//                ServletOutputStream outputStream = response.getOutputStream();
//                byte[] b = new byte[1024];
//                int len;
//                //从输入流中读取一定数量的字节，并将其存储在缓冲区字节数组中，读到末尾返回-1
//                while ((len = inputStream.read(b)) > 0) {
//                    outputStream.write(b, 0, len);
//                }
//            }catch (Exception e){
//                throw new ServiceException("文件下载失败");
//            }finally {
//                inputStream.close();
//            }
//        }else {
//            throw new ServiceException("系统路径不存在文件"+filePath);
//        }
//
//    }

    @Override
    public void download(HttpServletResponse response, Long id,String uploadPath) throws IOException {
        // 读到流中
        BusFileInfo file = Optional.ofNullable(this.getById(id))
            .orElseThrow(() -> new ServiceException("文件不存在"));
        //从 SysFile 对象获取文件绝对路径
        String filePath = file.getPath();
        String filename = file.getFilename() + "." + file.getExtension();
        nginxLocalDataDownload(response, uploadPath, filePath, filename);
    }

    @Override
    public void download(HttpServletResponse response, String filePath, String uploadPath, String fileName) {
        nginxLocalDataDownload(response, uploadPath, filePath, fileName);
        File file = new File(filePath);
        if (file.exists()) {
            file.deleteOnExit();
        }
    }

    @Override
    public void downloadThumbJpg(HttpServletResponse response, String filePath ,String uploadPath) throws IOException {
        File file = new File(filePath);
        if(!file.exists()){
            throw new GlobalException("该文件缩略图不存在，联系管理员修复");
        }
        String filename =  file.getName();
        nginxLocalDataDownload(response, uploadPath, filePath, filename);
    }
    private void nginxLocalDataDownload(HttpServletResponse response, String uploadPath, String filePath, String filename) {
        filePath=Path.of(filePath).toString();
        uploadPath=Path.of(uploadPath).toString();
        if (filePath.startsWith(uploadPath)) {
            int startIndex = uploadPath.length();
            // 处理路径分隔符
            if (startIndex < filePath.length() &&
                (filePath.charAt(startIndex) == '/' || filePath.charAt(startIndex) == '\\')) {
                startIndex++;
            }
            String relativePath = filePath.substring(startIndex);

            response.setHeader("X-Accel-Redirect", "/local_data/"+relativePath);
            response.setHeader("Content-Type", "application/octet-stream");
            response.setHeader("Content-Disposition", getDispositionFilename(filename));
        }else {
            throw new ServiceException("系统路径不存在文件"+filePath);
        }
    }


    private String getDispositionFilename(String filename) {
        try {
            // 标准的RFC 5987编码
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            return String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                encodedFilename, encodedFilename);
        } catch (Exception e) {
            // fallback to simple encoding
            return "attachment; filename=\"" + filename + "\"";
        }
    }



}
