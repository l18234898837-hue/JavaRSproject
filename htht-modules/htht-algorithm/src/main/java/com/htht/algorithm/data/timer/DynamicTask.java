package com.htht.algorithm.data.timer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

//@Component
public class DynamicTask implements SchedulingConfigurer {

//    @Autowired
    private TaskScheduler taskScheduler;

    // 模拟从数据库获取 cron 表达式（实际项目中可从 DB 读取）
    private String getCronExpression() {
        return "0 0/2 * * * ?"; // 每 2 分钟执行一次（可动态修改）
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // 注册动态任务
        registrar.setTaskScheduler(taskScheduler);
        registrar.addTriggerTask(
            () -> System.out.println("动态定时任务执行：" + LocalDateTime.now()), // 任务逻辑
            triggerContext -> new CronTrigger(getCronExpression()).nextExecutionTime(triggerContext) // 动态触发规则
        );
    }
}
