package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.multisource.infrastructure.worker.WorkerOperation;
import com.htht.multisource.infrastructure.worker.WorkerProtocolException;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class MultisourceExceptionHandlerTest {

    @Test
    void mapsExpiredManifestToStableMessageWithoutLeakingProcessDetails() {
        MultisourceExceptionHandler handler = new MultisourceExceptionHandler();
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/multisource/processing-tasks");
        WorkerProtocolException error = new WorkerProtocolException(
            "req-01", WorkerOperation.INSPECT,
            "INVALID_ASSET_MANIFEST", "manifest is expired", true
        );

        R<Void> response = handler.handleWorkerProtocolException(error, request);

        assertThat(response.getMsg()).isEqualTo("文件授权已过期，请重新点击开始处理");
        assertThat(response.getMsg()).doesNotContain("process exited with code 2");
        assertThat(response.getMsg()).doesNotContain("D:\\");
    }

    @Test
    void mapsOtherWorkerErrorsToGenericStableMessage() {
        MultisourceExceptionHandler handler = new MultisourceExceptionHandler();
        R<Void> response = handler.handleWorkerProtocolException(
            new WorkerProtocolException("req-02", WorkerOperation.EXECUTE_STAGE,
                "WORKER_FAILED", "local path C:\\secret\\input.tif", false),
            new MockHttpServletRequest("POST", "/api/multisource/processing-tasks/1/start")
        );

        assertThat(response.getMsg()).isEqualTo("Worker 执行失败，请稍后重试");
        assertThat(response.getMsg()).doesNotContain("C:\\secret");
    }
}
