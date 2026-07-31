package com.htht.thread;

import com.htht.thread.annotation.Pooled;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;


/**
 * 需要执行的线程任务
 *
 * @param <T>
 * @author wjx
 * @since 2023-5-4
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Worker<T> implements Runnable {

    //默认超时时间
    private static final long DEFAULT_TIMEOUT = 500;

    //执行指令
    private Runnable command;
    //返回结果
    private Result<T> result = new Result<>();
    //超时
    private long timeout;
    //策略
    private Pooled.PoolOverAct poolOverAct = Pooled.PoolOverAct.REJECT;
    //预备执行时间
    private volatile long prepareExecutionTime;
    //开始执行时间
    private volatile long startExecutionTime;
    //结束执行时间
    private volatile long endExecutionTime;
    //执行的线程池名称
    private String executorName;

    public Worker(Runnable command) {
        this.command = command;
        this.timeout = DEFAULT_TIMEOUT;
    }

    public Worker(Runnable command, Pooled.PoolOverAct poolOverAct) {
        this.command = command;
        this.timeout = DEFAULT_TIMEOUT;
        this.poolOverAct = poolOverAct;
    }

    public Worker(Runnable command, T result) {
        this.command = command;
        this.result = new Result<>(result);
        this.timeout = DEFAULT_TIMEOUT;
    }

    public Worker(Runnable command, T result, long timeout) {
        this.command = command;
        this.result = new Result<>(result);
        this.timeout = timeout;
    }

    @Override
    public void run() {
        startExecution();
        try {
            command.run();
        } catch (Exception e) {
            endExecution();
        }
    }

    /**
     * 开始执行（预备执行耗时)
     */
    private void startExecution() {
        this.startExecutionTime = System.currentTimeMillis();
        log.info("POOL_DISPATCH_TIME,EXECUTOR:{},TIME:{}ms", this.executorName, this.getPrepareTime());
    }

    /**
     * 结束执行（执行耗时）
     */
    private void endExecution() {
        this.endExecutionTime = System.currentTimeMillis();
        log.info("POOL_EXECUTE_TIME,EXECUTOR:{},TIME:{}ms", this.executorName, this.getExecutionTime());
    }

    /**
     * 预备耗时
     *
     * @return
     */
    public long getPrepareTime() {
        return this.startExecutionTime - this.prepareExecutionTime;
    }

    /**
     * 执行耗时
     *
     * @return
     */
    public long getExecutionTime() {
        return this.endExecutionTime - this.startExecutionTime;
    }

    /**
     * callable执行线程
     *
     * @return
     */
    public Callable<Result<T>> callable() {
        return Executors.callable(command, result);
    }

    public void setResult(T result) {
        if (null != this.result) {
            this.result.value = result;
        }
    }
}
