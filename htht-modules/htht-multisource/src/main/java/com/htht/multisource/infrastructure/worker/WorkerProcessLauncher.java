package com.htht.multisource.infrastructure.worker;

public interface WorkerProcessLauncher {

    default void reserve(String requestId) {
    }

    WorkerInvocationResult launch(WorkerInvocation invocation);

    void cancel(String requestId);

    default void release(String requestId) {
    }
}
