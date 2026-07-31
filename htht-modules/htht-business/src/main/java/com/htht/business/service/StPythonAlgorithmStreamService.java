package com.htht.business.service;

import com.htht.business.domain.StPythonAlgorithmStream;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.business.domain.vo.PythonAlgorithmStreamVo;

import java.util.List;

/**
 * <p>
 * Python算法执行流程表，记录算法组、执行顺序及关联的Python文件 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-15
 */
public interface StPythonAlgorithmStreamService extends IService<StPythonAlgorithmStream> {
    String[] getAlgorithmGroup();

    boolean groupIsOnly(String groupName);

    List<PythonAlgorithmStreamVo> getVoList();
}
