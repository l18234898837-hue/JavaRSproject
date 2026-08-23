package com.htht.multisource.infrastructure.worker;

public final class WorkerProtocolException extends RuntimeException {

    public WorkerProtocolException(String requestId, WorkerOperation operation, String message) {
        super("Worker protocol failure [" + operation.wireValue() + ", " + requestId + "]: " + message);
    }

    public WorkerProtocolException(String requestId, WorkerOperation operation, String message, Throwable cause) {
        super("Worker protocol failure [" + operation.wireValue() + ", " + requestId + "]: " + message, cause);
    }
}
