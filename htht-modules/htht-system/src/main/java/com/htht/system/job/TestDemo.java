package com.htht.system.job;

import com.htht.common.core.domain.entity.SysUser;
import com.htht.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.powerjob.worker.core.processor.ProcessResult;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.core.processor.sdk.BroadcastProcessor;

import java.util.List;

/**
 * 广播处理器 示例
 *
 * @author tjq
 * @since 2020/4/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDemo implements BroadcastProcessor {

    private final SysUserMapper sysUserMapper;

    @Override
    public ProcessResult process(TaskContext taskContext) throws Exception {
        System.out.println("测试定时任务");
        List<SysUser> sysUsers = sysUserMapper.selectList();
        System.out.println(sysUsers);
        return null;
    }
}
