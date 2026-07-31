package com.htht.algorithm.thread.aop;

import org.springframework.core.Ordered;

/**
 * Aop的拦截顺序定义
 *
 * @author wjx
 * @since 2023-5-5
 */
public class AopOrder {

    public static final int DISTRIBUTED_LOCK_ORDER = Ordered.HIGHEST_PRECEDENCE + 4;
    public static final int POOLED_INVOKER_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

}
