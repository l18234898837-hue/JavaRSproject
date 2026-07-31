package com.htht.business.mapper;

import com.htht.business.domain.StPythonAlgorithmStream;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * Python算法执行流程表，记录算法组、执行顺序及关联的Python文件 Mapper 接口
 * </p>
 *
 * @author MSFY
 * @since 2025-08-15
 */
public interface StPythonAlgorithmStreamMapper extends BaseMapper<StPythonAlgorithmStream> {
    String[] getAlgorithmGroup();

}
