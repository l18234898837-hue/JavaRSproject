package com.htht.business.service;

import com.htht.business.domain.StPythonAlgorithmFileInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>
 * Python算法文件元数据表，关联算法增删改查流程 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-14
 */
public interface StPythonAlgorithmFileInfoService extends IService<StPythonAlgorithmFileInfo> {

    boolean pythonFileNameIsOnly(String name);


    boolean add(MultipartFile file, String algorithmName, String description);

    void getPyFile(HttpServletResponse response, Long id) throws IOException;

    boolean updateFile(MultipartFile file, Long id);

    TableDataInfo<StPythonAlgorithmFileInfo> page(StPythonAlgorithmFileInfo stPythonAlgorithmFileInfo, PageQuery pageQuery);
}
