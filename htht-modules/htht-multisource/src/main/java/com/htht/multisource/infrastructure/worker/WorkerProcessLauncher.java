package com.htht.multisource.infrastructure.worker;

public interface WorkerProcessLauncher {

    WorkerInvocationResult launch(WorkerInvocation invocation);

    void cancel(String requestId);
}
