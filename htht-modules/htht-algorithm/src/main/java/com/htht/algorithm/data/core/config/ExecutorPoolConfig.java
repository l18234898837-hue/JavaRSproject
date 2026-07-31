package com.htht.algorithm.data.core.config;

import com.htht.algorithm.thread.TaskToolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册各种业务线程池
 *
 * @author wjx
 * @since 2023-5-5
 */
@Configuration
public class ExecutorPoolConfig {

    /**
     * 矢量入库工具类线程池
     *
     * @return
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")
    public TaskToolExecutor vectorStorageExecutor() {
        TaskToolExecutor vectorStorageExecutor = new TaskToolExecutor();
        vectorStorageExecutor.setName("vectorStorageExecutor");
        vectorStorageExecutor.setCoreSize(15);
        vectorStorageExecutor.setMaxSize(32);
        vectorStorageExecutor.setQueueSize(1024);
        return vectorStorageExecutor;
    }

    /**
     * 栅格入库工具类线程池
     *
     * @return
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")
    public TaskToolExecutor gridStorageExecutor() {
        TaskToolExecutor gridStorageExecutor = new TaskToolExecutor();
        gridStorageExecutor.setName("gridStorageExecutor");
        gridStorageExecutor.setCoreSize(15);
        gridStorageExecutor.setMaxSize(32);
        gridStorageExecutor.setQueueSize(1024);
        return gridStorageExecutor;
    }

    /**
     * nc文件解析工具类线程池
     *
     * @return
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")
    public TaskToolExecutor ncStorageExecutor() {
        TaskToolExecutor ncStorageExecutor = new TaskToolExecutor();
        ncStorageExecutor.setName("ncStorageExecutor");
        ncStorageExecutor.setCoreSize(15);
        ncStorageExecutor.setMaxSize(32);
        ncStorageExecutor.setQueueSize(1024);
        return ncStorageExecutor;
    }

    /**
     * 图片入库工具类线程池
     *
     * @return
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")
    public TaskToolExecutor imageStorageExecutor() {
        TaskToolExecutor imageStorageExecutor = new TaskToolExecutor();
        imageStorageExecutor.setName("imageStorageExecutor");
        imageStorageExecutor.setCoreSize(15);
        imageStorageExecutor.setMaxSize(32);
        imageStorageExecutor.setQueueSize(1024);
        return imageStorageExecutor;
    }

    /**
     * 切片缓存清理线程池
     *
     * @return
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")
    public TaskToolExecutor clearTileCacheExecutor() {
        TaskToolExecutor clearTileCacheExecutor = new TaskToolExecutor();
        clearTileCacheExecutor.setName("clearTileCacheExecutor");
        clearTileCacheExecutor.setCoreSize(15);
        clearTileCacheExecutor.setMaxSize(32);
        clearTileCacheExecutor.setQueueSize(1024);
        return clearTileCacheExecutor;
    }

    /**
     * 在线数据下载线程池
     *
     * @return
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")
    public TaskToolExecutor OnlineDownloadExecutor() {
        TaskToolExecutor OnlineDownloadExecutor = new TaskToolExecutor();
        OnlineDownloadExecutor.setName("OnlineDownloadExecutor");
        OnlineDownloadExecutor.setCoreSize(15);
        OnlineDownloadExecutor.setMaxSize(32);
        OnlineDownloadExecutor.setQueueSize(1024);
        return OnlineDownloadExecutor;
    }

    /**
     * 计算移动文件线程池
     *
     * @return
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")
    public TaskToolExecutor AlgorithmRecordExecutor() {
        TaskToolExecutor AlgorithmRecordExecutor = new TaskToolExecutor();
        AlgorithmRecordExecutor.setName("AlgorithmRecordExecutor");
        AlgorithmRecordExecutor.setCoreSize(15);
        AlgorithmRecordExecutor.setMaxSize(32);
        AlgorithmRecordExecutor.setQueueSize(1024);
        return AlgorithmRecordExecutor;
    }
}
