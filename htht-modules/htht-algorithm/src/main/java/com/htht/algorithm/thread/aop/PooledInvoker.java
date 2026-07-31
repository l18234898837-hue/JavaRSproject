package com.htht.algorithm.thread.aop;

import com.htht.algorithm.thread.TaskToolExecutor;
import com.htht.algorithm.thread.Worker;
import com.htht.algorithm.thread.annotation.Pooled;
import com.htht.algorithm.thread.utils.PrimitiveUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@Order(AopOrder.POOLED_INVOKER_ORDER)
@Slf4j
@RequiredArgsConstructor
public class PooledInvoker {

    private final Map<String, TaskToolExecutor> executorMap;

    @Around("@annotation(com.htht.algorithm.thread.annotation.Pooled)&&@annotation(pooled)")
    public Object around(final ProceedingJoinPoint pjp, Pooled pooled) {
        TaskToolExecutor executor = getExecutor(pooled.executor());
        Object result = null;
        Worker<Object> worker = toWorker(pjp);
        worker.setPoolOverAct(pooled.poolOverAct());
        if (pooled.async()) {
            executor.execute(worker);
        } else {
            worker.setTimeout(pooled.timeout());
            result = executor.submit(worker);
        }
        if (null == result) {
            Class<?> returnType = ((MethodSignature) pjp.getSignature()).getMethod().getReturnType();
            if (returnType.isPrimitive()) {
                return PrimitiveUtils.getPrimitiveDefaultValue(returnType);
            }
        }
        return result;
    }

    private Worker<Object> toWorker(ProceedingJoinPoint pjp) {
        final Worker<Object> worker = new Worker<>();
        Runnable command = () -> {
            try {
                worker.setResult(pjp.proceed());
            } catch (Throwable e) {
                log.error("Error pooled execute:{}", e);
            }
        };
        worker.setCommand(command);
        return worker;
    }

    private TaskToolExecutor getExecutor(String poolType) {
        return executorMap.get(poolType);
    }
}
