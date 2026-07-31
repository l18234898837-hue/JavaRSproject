package com.htht.business.service.impl;

import com.htht.business.domain.StPythonAlgorithmUserStream;
import com.htht.business.mapper.StPythonAlgorithmUserStreamMapper;
import com.htht.business.service.StPythonAlgorithmUserStreamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户、执行流程与数据集关联表，含名称和数字编码的执行状态，用于查询执行后的流程信息 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-15
 */
@Service
public class StPythonAlgorithmUserStreamServiceImpl extends ServiceImpl<StPythonAlgorithmUserStreamMapper, StPythonAlgorithmUserStream> implements StPythonAlgorithmUserStreamService {

}
