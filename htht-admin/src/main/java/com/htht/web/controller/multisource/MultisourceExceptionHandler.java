package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.multisource.infrastructure.worker.WorkerFailurePresentation;
import com.htht.multisource.infrastructure.worker.WorkerProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice(basePackages = "com.htht.web.controller.multisource")
public final class MultisourceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MultisourceExceptionHandler.class);
    @ExceptionHandler(WorkerProtocolException.class)
    public R<Void> handleWorkerProtocolException(WorkerProtocolException error, HttpServletRequest request) {
        if (WorkerFailurePresentation.GF6_OUTPUT_LIMIT_EXCEEDED.equals(error.code())) {
            log.warn("请求地址'{}' GF-6 输出超过安全上限 requestId={}", request.getRequestURI(), error.requestId());
            return R.fail(WorkerFailurePresentation.GF6_OUTPUT_LIMIT_MESSAGE);
        }
        WorkerFailurePresentation.SafeFailure safeFailure = WorkerFailurePresentation.forClient(
            error.code(), error.workerMessage()
        );
        if ("INVALID_REQUEST".equals(safeFailure.code())) {
            log.warn("请求地址'{}' Worker 参数校验失败 requestId={}", request.getRequestURI(), error.requestId());
            return R.fail(WorkerFailurePresentation.restMessage(error.code(), error.workerMessage()));
        }
        log.error("请求地址'{}' Worker 调用失败 requestId={} safeCode={} manifestExpired={}",
            request.getRequestURI(), error.requestId(), safeFailure.code(), error.isManifestExpired());
        return R.fail(error.isManifestExpired()
            ? "文件授权已过期，请重新点击开始处理"
            : safeFailure.message());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public R<Void> handleIllegalState(IllegalStateException error, HttpServletRequest request) {
        if ("Preflight session is unavailable.".equals(error.getMessage())) {
            log.warn("请求地址'{}' 的预检会话已失效，请重新预检", request.getRequestURI());
            return R.fail("预检会话已失效，请重新点击预检或开始处理");
        }
        String message = error.getMessage();
        if (message != null && (message.contains("临时空间不足") || message.contains("解析任务繁忙")
            || message.contains("解析服务正在停止"))) {
            log.warn("请求地址'{}' 多源归档资源受限: {}", request.getRequestURI(), message);
            return R.fail(message);
        }
        log.error("请求地址'{}' 多源处理状态异常: {}", request.getRequestURI(), error.getMessage(), error);
        return R.fail("多源处理状态不可用，请重试");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgument(IllegalArgumentException error, HttpServletRequest request) {
        log.warn("请求地址'{}' 多源归档请求参数无效: {}", request.getRequestURI(), error.getMessage());
        return R.fail(error.getMessage() == null ? "请求参数无效" : error.getMessage());
    }
}
