package com.htht.algorithm.algorithm.service;

import com.htht.algorithm.algorithm.domain.SdAlgorithmFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.algorithm.domain.dto.*;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmFileVo;
import com.htht.algorithm.download.domain.vo.FileTreeVO;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
* 算法文件表 服务类
*
* @author MSFY
* @since 2026-07-21
*/
public interface ISdAlgorithmFileService extends IService<SdAlgorithmFile> {

    boolean add(SdAlgorithmFileAdd addSdAlgorithmFile);

    boolean up(SdAlgorithmFileUp upSdAlgorithmFile);

    List<SdAlgorithmFileVo> getList(SdAlgorithmFileQuery query);

    SdAlgorithmFileVo getVoById(Long id);

    TableDataInfo<SdAlgorithmFileVo> pageByQuery(SdAlgorithmFileQuery query, PageQuery pageQuery);

    void run(SdAlgorithmStartByVo start);

    void run(SdAlgorithmStartById start);

    FileTreeVO getInput(Long id);

    FileTreeVO getOutput(Long id);
}
