package com.htht.algorithm.thread.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 线程池切面
 *
 * @author wjx
 * @since 2023-5-4
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Pooled {

    boolean async() default true;

    long timeout() default 500;

    String executor() default "ciToolExecutor";

    PoolOverAct poolOverAct() default PoolOverAct.REJECT;

    enum PoolOverAct {
        REJECT, BLOCK, RUN, NEW_THREAD;
    }
}
