package com.htht.multisource.infrastructure.worker;

/**
 * Produces the small, explicitly safe subset of worker failures that may cross
 * the worker-to-client boundary. Worker protocol fields are otherwise treated
 * as untrusted process output.
 */
public final class WorkerFailurePresentation {

    public static final String GF6_OUTPUT_LIMIT_EXCEEDED = "GF6_OUTPUT_LIMIT_EXCEEDED";
    public static final String MEMORY_LIMIT_EXCEEDED = "MEMORY_LIMIT_EXCEEDED";
    public static final String WORKER_STAGE_TIMEOUT = "WORKER_STAGE_TIMEOUT";
    public static final String WORKER_DISPATCH_FAILED = "WORKER_DISPATCH_FAILED";
    public static final String GENERIC_WORKER_FAILURE_MESSAGE = "Worker 执行失败，请稍后重试";
    public static final String WORKER_STAGE_TIMEOUT_MESSAGE = "处理阶段超过服务端执行时限，请调整输出范围或联系管理员后重试";
    public static final String GF6_OUTPUT_LIMIT_MESSAGE = GF6_OUTPUT_LIMIT_EXCEEDED
        + ": 输出网格超过服务端安全上限，请增大输出分辨率、缩小 ROI、减少波段或关闭部分衍生产物";
    public static final String MEMORY_LIMIT_MESSAGE = "影像处理内存不足，请缩小 ROI、减少波段或使用分块处理配置";

    private static final String INVALID_REQUEST = "INVALID_REQUEST";
    private static final int MAX_ACTIONABLE_MESSAGE_LENGTH = 240;

    private WorkerFailurePresentation() {
    }

    public static SafeFailure forClient(String workerCode, String workerMessage) {
        if (GF6_OUTPUT_LIMIT_EXCEEDED.equals(workerCode)) {
            return new SafeFailure(GF6_OUTPUT_LIMIT_EXCEEDED, GF6_OUTPUT_LIMIT_MESSAGE);
        }
        if (MEMORY_LIMIT_EXCEEDED.equals(workerCode)) {
            return new SafeFailure(MEMORY_LIMIT_EXCEEDED, MEMORY_LIMIT_MESSAGE);
        }
        if (WORKER_STAGE_TIMEOUT.equals(workerCode)) {
            return new SafeFailure(WORKER_STAGE_TIMEOUT, WORKER_STAGE_TIMEOUT_MESSAGE);
        }
        if (INVALID_REQUEST.equals(workerCode) && isSafeActionableMessage(workerMessage)) {
            return new SafeFailure(INVALID_REQUEST, workerMessage.strip());
        }
        return new SafeFailure(WORKER_DISPATCH_FAILED, GENERIC_WORKER_FAILURE_MESSAGE);
    }

    public static String restMessage(String workerCode, String workerMessage) {
        SafeFailure failure = forClient(workerCode, workerMessage);
        return INVALID_REQUEST.equals(failure.code())
            ? "输入参数错误：" + failure.message()
            : failure.message();
    }

    private static boolean isSafeActionableMessage(String workerMessage) {
        if (workerMessage == null) {
            return false;
        }
        String message = workerMessage.strip();
        if (message.isEmpty() || message.length() > MAX_ACTIONABLE_MESSAGE_LENGTH) {
            return false;
        }
        return message.codePoints().allMatch(WorkerFailurePresentation::isAllowedActionableCharacter);
    }

    private static boolean isAllowedActionableCharacter(int character) {
        return Character.isLetterOrDigit(character)
            || Character.isSpaceChar(character)
            || character == '.'
            || character == ','
            || character == '_'
            || character == '('
            || character == ')'
            || character == '-';
    }

    public record SafeFailure(String code, String message) {
    }
}
