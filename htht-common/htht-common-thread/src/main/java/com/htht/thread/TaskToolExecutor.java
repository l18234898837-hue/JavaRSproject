package com.htht.thread;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务线程池
 *
 * @author wjx
 * @since 2023-5-4
 */
@Data
@Slf4j
public class TaskToolExecutor {

    /**
     * 默认核心线程数
     */
    private static final int DEFAULT_CORE_SIZE = 20;

    /**
     * 默认最大线程数
     */
    private static final int DEFAULT_MAX_SIZE = 50;
    /**
     * 默认空闲线程存货时间
     */
    private static final long DEFAULT_ALIVE_TIME = 60;
    /**
     * 默认队列数量
     */
    private static final int DEFAULT_QUEUE_SIZE = 1024;

    /**
     * 线程池
     */
    private ExecutorService pool;
    /**
     * 线程工厂
     */
    private ThreadFactory threadFactory;
    /**
     * 任务较多时暂存队列
     */
    private BlockingQueue<Runnable> workQueue;

    /**
     * 核心线程数
     */
    private int coreSize;

    /**
     * 最大线程数
     */
    private int maxSize;
    /**
     * 空闲线程存活时间
     */
    private long aliveTime;
    /**
     * 队列数量
     */
    private int queueSize;
    /**
     * 线程池名称
     */
    private String name;

    /**
     * 线程池初始化
     */
    public void init() {
        if (null == pool) {
            if (null == workQueue) {
                queueSize = queueSize > 0 ? queueSize : DEFAULT_QUEUE_SIZE;
                workQueue = new LinkedBlockingQueue<>(queueSize);
            }
            if (null == threadFactory) {
                threadFactory = TaskToolExecutor.defaultThreadFactory();
            }
            coreSize = coreSize > 0 ? coreSize : DEFAULT_CORE_SIZE;
            maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
            aliveTime = aliveTime > 0 ? aliveTime : DEFAULT_ALIVE_TIME;
            pool = new ThreadPoolExecutor(coreSize, maxSize, aliveTime, TimeUnit.SECONDS, workQueue, threadFactory);
        }
    }

    /**
     * 销毁线程池
     */
    public void destroy() {
        this.pool.shutdown();
    }

    /**
     * 执行Task
     *
     * @param worker
     */
    public void execute(Worker<?> worker) {
        try {
            worker.setExecutorName(this.name);
            worker.setPrepareExecutionTime(System.currentTimeMillis());
            pool.execute(worker);
        } catch (RejectedExecutionException e) {
            //拒绝策略
            dealWhenPoolFull(worker, e);
        }
    }

    /**
     * 提交Task，可获取线程返回结果
     *
     * @param worker
     * @param <T>
     * @return
     */
    public <T> T submit(Worker<T> worker) {
        try {
            Future<Result<T>> future = pool.submit(worker.callable());
            Result<T> result = future.get(worker.getTimeout(), TimeUnit.MILLISECONDS);
            return result.value;
        } catch (InterruptedException e) {
            log.error("Interrupted worker:{}", e);
        } catch (ExecutionException e) {
            log.error("Attempting to retrieve the result of a task that aborted:{}", e);
        } catch (TimeoutException e) {
            log.error("Timeout worker:get result timeout:{}", e);
        } catch (RejectedExecutionException e) {
            log.error("Rejected worker:Perhaps thread pool is full:{}", e);
        }
        return worker.getResult().value;
    }

    /**
     * 线程池占满之后拒绝策略
     *
     * @param worker
     * @param e
     */
    private void dealWhenPoolFull(Worker<?> worker, RejectedExecutionException e) {
        switch (worker.getPoolOverAct()) {
            case RUN:
                worker.run();
                break;
            case BLOCK:
                try {
                    workQueue.put(worker);
                } catch (InterruptedException ex) {
                    log.error("queue put worker:Perhaps block queue is full!{}", e);
                }
                break;
            case NEW_THREAD:
                Thread newThreadOutOfPool = threadFactory.newThread(worker);
                newThreadOutOfPool.setName("outOfPool-" + newThreadOutOfPool.getName());
                newThreadOutOfPool.start();
                break;
            default:
                log.error("Rejected worker:Perhaps thread pool is full!{}", e);
                break;
        }
    }

    /**
     * 默认线程工厂
     */
    static class DefaultThreadFactory implements ThreadFactory {

        static final AtomicInteger poolNumber = new AtomicInteger(1);
        final ThreadGroup group;
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;

        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "taskTool-" + poolNumber.getAndIncrement() + "-thead-";
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(group, runnable, namePrefix + threadNumber.getAndIncrement(), 0);
            //守护线程
            if (thread.isDaemon()) {
                thread.setDaemon(false);
            }
            //线程优先级
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            return thread;
        }
    }

    public static ThreadFactory defaultThreadFactory() {
        return new DefaultThreadFactory();
    }

    public void setQueueSize(int queueSize) {
        if (queueSize <= 0) {
            this.queueSize = DEFAULT_QUEUE_SIZE;
        } else {
            this.queueSize = queueSize;
        }
    }
}
