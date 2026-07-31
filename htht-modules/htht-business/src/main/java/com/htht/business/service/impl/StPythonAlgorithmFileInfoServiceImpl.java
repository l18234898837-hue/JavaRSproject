package com.htht.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.config.PythonProperties;
import com.htht.business.domain.StPythonAlgorithmFileInfo;
import com.htht.business.mapper.StPythonAlgorithmFileInfoMapper;
import com.htht.business.service.StPythonAlgorithmFileInfoService;
import com.htht.business.utils.PythonUtil;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>
 * Python算法文件元数据表，关联算法增删改查流程 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-14
 */
@Service
@RequiredArgsConstructor
public class StPythonAlgorithmFileInfoServiceImpl extends ServiceImpl<StPythonAlgorithmFileInfoMapper, StPythonAlgorithmFileInfo> implements StPythonAlgorithmFileInfoService {
    private final PythonProperties pythonProperties;

    @Override
    public boolean pythonFileNameIsOnly(String name) {
        return  this.lambdaQuery().
            eq(StPythonAlgorithmFileInfo::getPythonFileName,name)
            .or()
            .eq(StPythonAlgorithmFileInfo::getAlgorithmName,name)
            .list().isEmpty();
    }

    @Override
    public boolean add(MultipartFile file, String algorithmName, String description) {
        if(!PythonUtil.isPy(Objects.requireNonNull(file.getOriginalFilename()))){
            throw new GlobalException("请传入py文件");
        }
        if(!pythonFileNameIsOnly(file.getOriginalFilename())){
            throw new GlobalException("文件名重复,已有同文件夹名加入算法");
        }
        if(!pythonFileNameIsOnly(algorithmName)){
            throw new GlobalException("已有同名算法");
        }
        String filePath = PythonUtil.savePyFile(pythonProperties.getFilePath(), file);
        StPythonAlgorithmFileInfo stPythonAlgorithmFileInfo = new StPythonAlgorithmFileInfo()
            .setPythonFileName(file.getOriginalFilename())
            .setFileUrl(filePath)
            .setAlgorithmName(algorithmName)
            .setDescription(description);
        return this.save(stPythonAlgorithmFileInfo);

    }

    @Override
    public void getPyFile(HttpServletResponse response, Long id)  {
        StPythonAlgorithmFileInfo stPythonAlgorithmFileInfo = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("不存在该算法"));
        try (FileInputStream inputStream = new FileInputStream(stPythonAlgorithmFileInfo.getFileUrl())) {
            response.reset();
            response.setContentType("application/octet-stream");
            String filename = new File(stPythonAlgorithmFileInfo.getFileUrl()).getName();
            response.addHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            ServletOutputStream outputStream = response.getOutputStream();
            byte[] b = new byte[1024];
            int len;
            //从输入流中读取一定数量的字节，并将其存储在缓冲区字节数组中，读到末尾返回-1
            while ((len = inputStream.read(b)) > 0) {
                outputStream.write(b, 0, len);
            }
        } catch (IOException e) {
            throw new GlobalException("文件不存在");
        }
    }

    @Override
    public boolean updateFile(MultipartFile file, Long id) {
        StPythonAlgorithmFileInfo stPythonAlgorithmFileInfo = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("不存在该算法"));
        if(!PythonUtil.isPy(Objects.requireNonNull(file.getOriginalFilename()))){
            throw new GlobalException("请传入py文件");
        }
       if(! stPythonAlgorithmFileInfo.getPythonFileName().equals(file.getOriginalFilename())){
           throw new GlobalException("请上传同文件名py文件");
       }
       PythonUtil.savePyFile(pythonProperties.getFilePath(), file);
       return true;
    }

    @Override
    public TableDataInfo<StPythonAlgorithmFileInfo> page(StPythonAlgorithmFileInfo stPythonAlgorithmFileInfo, PageQuery pageQuery) {
        return TableDataInfo.build(
            this.lambdaQuery()
            .eq(ObjectUtils.isNotNull(stPythonAlgorithmFileInfo.getId()),StPythonAlgorithmFileInfo::getId, stPythonAlgorithmFileInfo.getId())
            .like(Strings.isNotBlank(stPythonAlgorithmFileInfo.getAlgorithmName()),StPythonAlgorithmFileInfo::getAlgorithmName, stPythonAlgorithmFileInfo.getAlgorithmName())
            .page(new Page<>(pageQuery.getPageNum(),pageQuery.getPageSize()))
        );
    }
}
