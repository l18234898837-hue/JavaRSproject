package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.multisource.infrastructure.worker.WorkerProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice(basePackages = "com.htht.web.controller.multisource")
public final class MultisourceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MultisourceExceptionHandler.class);

    @ExceptionHandler(WorkerProtocolException.class)
    public R<Void> handleWorkerProtocolException(WorkerProtocolException error, HttpServletRequest request) {
        log.error("请求地址'{}' Worker 调用失败 requestId={}: {}",
            request.getRequestURI(), error.requestId(), error.getMessage(), error);
        return R.fail(error.isManifestExpired()
            ? "文件授权已过期，请重新点击开始处理"
            : "Worker 执行失败，请稍后重试");
    }
}
