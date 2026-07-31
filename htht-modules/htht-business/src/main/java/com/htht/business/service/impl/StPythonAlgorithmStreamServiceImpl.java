package com.htht.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.business.domain.StPythonAlgorithmStream;
import com.htht.business.domain.vo.PythonAlgorithmStreamVo;
import com.htht.business.mapper.StPythonAlgorithmStreamMapper;
import com.htht.business.service.StPythonAlgorithmStreamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Python算法执行流程表，记录算法组、执行顺序及关联的Python文件 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-15
 */
@Service
@RequiredArgsConstructor
public class StPythonAlgorithmStreamServiceImpl extends ServiceImpl<StPythonAlgorithmStreamMapper, StPythonAlgorithmStream> implements StPythonAlgorithmStreamService {

    @Override
    public String[] getAlgorithmGroup() {
        return this.baseMapper.getAlgorithmGroup();
    }

    @Override
    public boolean groupIsOnly(String groupName) {
         return this.lambdaQuery()
            .eq(StPythonAlgorithmStream::getAlgorithmGroup, groupName)
            .list()
            .isEmpty();
    }

    @Override
    public List<PythonAlgorithmStreamVo> getVoList() {
        //分组获得所有算法组
        String[] algorithmGroup = getAlgorithmGroup();
        //创建返回用的vo集合
        List<PythonAlgorithmStreamVo> Templist=new ArrayList<>();
        //遍历所有分组
        for (String  groupName : algorithmGroup) {
            //获取分组下的所有算法，以及顺序
            List<StPythonAlgorithmStream> StPythonAlgorithmStreamList = this.list(
                new LambdaQueryWrapper<StPythonAlgorithmStream>()
                    .eq(StPythonAlgorithmStream::getAlgorithmGroup, groupName)
            );
            PythonAlgorithmStreamVo pythonAlgorithmStreamVo = new PythonAlgorithmStreamVo(StPythonAlgorithmStreamList);
            Templist.add(pythonAlgorithmStreamVo);
        }
        return Templist;
    }
}
