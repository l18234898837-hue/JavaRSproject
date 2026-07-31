package com.htht.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.exception.ServiceException;
import com.htht.system.domain.SysFile;
import com.htht.system.mapper.SysFileMapper;
import com.htht.system.service.ISysFileService;
import jodd.io.FileNameUtil;
import org.springframework.beans.factory.annotation.Value;
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * @author lzs
 * @description 针对表【upload_file】的数据库操作Service实现
 * @createDate 2023-04-04 10:06:37
 */
@Service
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile>
    implements ISysFileService {

    private static final String[] BAN_EXTENSION = {".asp",".jsp",".aspx",".php",".exe",".vbs",".com"};
    //静态文件后缀
    public static final String[] STATIC_EXTENSIONS = {
        // 图片文件
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg", ".ico",
        // 文档文件
        ".pdf",
    };
    @Value("${file.upload-path}")
    private String path;

    @Override
    public SysFile upload(MultipartFile file) {

        String absolutePath = path;

        boolean isStatics = false;

        //获取文件后缀.结尾的后缀
        String filename = file.getOriginalFilename();
        String extension = file.getOriginalFilename().substring(filename.lastIndexOf("."));
        String folder = filename.substring(filename.lastIndexOf(".") + 1);

        if (Arrays.asList(STATIC_EXTENSIONS).contains(extension)){
            isStatics = true;
            absolutePath = absolutePath + "/static";
        }

        String fileName = file.getOriginalFilename();
        if (Arrays.asList(BAN_EXTENSION).contains(extension)){
            throw new IllegalArgumentException("上传的文件不合法!");
        }
        Long size = file.getSize();
        SysFile uploadFileInfo = new SysFile();
        uploadFileInfo.setFileName(FileNameUtil.getBaseName(fileName));
        uploadFileInfo.setExtension(FileNameUtil.getExtension(fileName));
        uploadFileInfo.setTotalSize(size);

        String fileFolder = absolutePath + "/"+folder;
        File folderFile = new File(fileFolder);
        if (!folderFile.exists()) {
            folderFile.mkdirs();
        }

        String fileId = UUID.randomUUID().toString().replace("-", "");
        String newFileName = fileId + "." + FileNameUtil.getExtension(fileName);
        String filePath = fileFolder + "/" + newFileName;
        try {
            file.transferTo(new File(filePath));
            // 强制设置 644 权限（容器内/宿主机都会生效）
            // 判断当前系统是否支持 POSIX 权限（Windows 不支持）
            boolean isPosixSupported = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

            if (isPosixSupported) {
                // 类 Unix 系统：设置 POSIX 644 权限
                Path targetPath = Paths.get(filePath);
                Set<PosixFilePermission> permissions = new HashSet<>();
                permissions.add(PosixFilePermission.OWNER_READ);
                permissions.add(PosixFilePermission.OWNER_WRITE);
                permissions.add(PosixFilePermission.GROUP_READ);
                permissions.add(PosixFilePermission.OTHERS_READ);
                Files.setPosixFilePermissions(targetPath, permissions);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        uploadFileInfo.setFilePath(filePath);
        uploadFileInfo.setIsStatic(isStatics?1:0);
        if (isStatics) {
            uploadFileInfo.setStaticPath(folder + "/" + newFileName);
        }
        this.save(uploadFileInfo);
        return uploadFileInfo;
    }

    @Override
    public void download(HttpServletResponse response, Long id) throws IOException {
        // 读到流中
        SysFile sysFile = Optional.ofNullable(this.getById(id))
            .orElseThrow(() -> new ServiceException("文件不存在"));

        //从 SysFile 对象获取文件绝对路径
        String filePath = sysFile.getFilePath();

        if (filePath.startsWith(path)) {
            // 读到流中
            InputStream inputStream = new FileInputStream(sysFile.getFilePath());// 文件的存放路径
            try {
                response.reset();
                response.setContentType("application/octet-stream");
                String filename = new File(sysFile.getFilePath()).getName();
                response.addHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, "UTF-8"));
                ServletOutputStream outputStream = response.getOutputStream();
                byte[] b = new byte[1024];
                int len;
                //从输入流中读取一定数量的字节，并将其存储在缓冲区字节数组中，读到末尾返回-1
                while ((len = inputStream.read(b)) > 0) {
                    outputStream.write(b, 0, len);
                }
            }catch (Exception e){
                throw new ServiceException("文件下载失败");
            }finally {
                inputStream.close();
            }
        }else {
            throw new ServiceException("系统路径不存在文件"+sysFile.getFilePath());
        }

    }

    @Override
    public boolean delete(Long id) {

        SysFile sysFile = Optional.ofNullable(this.getById(id))
            .orElseThrow(() -> new ServiceException("文件不存在"));

        //从 SysFile 对象获取文件绝对路径
        String filePath = sysFile.getFilePath();

        if (filePath.startsWith(path)) {
            // 删除文件
            // 初始化 File 对象
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                // 尝试删除文件并返回结果
                file.delete();
            }
        }
        return this.removeById(sysFile.getId());
    }


    @Override
    public void download(HttpServletResponse response, Long id,String uploadPath) throws IOException {
        uploadPath=path;
        // 读到流中
        SysFile file = Optional.ofNullable(this.getById(id))
            .orElseThrow(() -> new ServiceException("文件不存在"));

        //从 SysFile 对象获取文件绝对路径
        String filePath = file.getFilePath();



        String filename = file.getFileName() + "." + file.getExtension();

        if (filePath.startsWith(uploadPath)) {
            int startIndex = uploadPath.length();
            // 处理路径分隔符
            if (startIndex < filePath.length() &&
                (filePath.charAt(startIndex) == '/' || filePath.charAt(startIndex) == '\\')) {
                startIndex++;
            }
            String relativePath = filePath.substring(startIndex);

            response.setHeader("X-Accel-Redirect", "/sys_data/"+relativePath);
            response.setHeader("Content-Type", "application/octet-stream");
            response.setHeader("Content-Disposition",
                getDispositionFilename(filename));
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




