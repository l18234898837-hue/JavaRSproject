package com.htht.multisource.infrastructure.worker;

public final class WorkerProtocolException extends RuntimeException {

    private final String code;
    private final boolean retryable;
    private final String workerMessage;
    private final String requestId;
    private final WorkerOperation operation;

    public WorkerProtocolException(String requestId, WorkerOperation operation, String message) {
        this(requestId, operation, null, message, false, null);
    }

    public WorkerProtocolException(String requestId, WorkerOperation operation, String message, Throwable cause) {
        this(requestId, operation, null, message, false, cause);
    }

    public WorkerProtocolException(
        String requestId, WorkerOperation operation, String code, String message, boolean retryable
    ) {
        this(requestId, operation, code, message, retryable, null);
    }

    private WorkerProtocolException(
        String requestId, WorkerOperation operation, String code, String message, boolean retryable, Throwable cause
    ) {
        super("Worker protocol failure [" + operation.wireValue() + ", " + requestId + "]: "
            + (code == null ? message : code + ": " + message + " (retryable=" + retryable + ")"), cause);
        this.code = code;
        this.retryable = retryable;
        this.workerMessage = message;
        this.requestId = requestId;
        this.operation = operation;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public boolean isManifestExpired() {
        return "INVALID_ASSET_MANIFEST".equals(code) && "manifest is expired".equals(workerMessage);
    }

    public String requestId() {
        return requestId;
    }

    public WorkerOperation operation() {
        return operation;
    }
}
