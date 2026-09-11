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

    @Test
    void exposesGf6OutputLimitCodeWithSafeMessageWithoutWorkerDetails() {
        MultisourceExceptionHandler handler = new MultisourceExceptionHandler();

        R<Void> response = handler.handleWorkerProtocolException(
            new WorkerProtocolException("req-03", WorkerOperation.EXECUTE_STAGE,
                "GF6_OUTPUT_LIMIT_EXCEEDED", "output grid C:\\secret\\work\\grid.tif is too large", false),
            new MockHttpServletRequest("POST", "/api/multisource/processing-tasks/1/dispatch")
        );

        assertThat(response.getMsg()).isEqualTo(
            "GF6_OUTPUT_LIMIT_EXCEEDED: 输出网格超过服务端安全上限，请增大输出分辨率、缩小 ROI、减少波段或关闭部分衍生产物"
        );
        assertThat(response.getMsg()).doesNotContain("C:\\secret");
    }

    @Test
    void returnsActionableMessageForInvalidWorkerParameters() {
        MultisourceExceptionHandler handler = new MultisourceExceptionHandler();

        R<Void> response = handler.handleWorkerProtocolException(
            new WorkerProtocolException("req-03", WorkerOperation.BUILD_PLAN,
                "INVALID_REQUEST", "L2A ROI bounds are invalid", false),
            new MockHttpServletRequest("POST", "/api/multisource/processing-tasks")
        );

        assertThat(response.getMsg()).isEqualTo("输入参数错误：L2A ROI bounds are invalid");
    }

    @Test
    void doesNotExposeUnsafeInvalidRequestDetails() {
        MultisourceExceptionHandler handler = new MultisourceExceptionHandler();

        R<Void> response = handler.handleWorkerProtocolException(
            new WorkerProtocolException("req-04", WorkerOperation.BUILD_PLAN,
                "INVALID_REQUEST", "invalid ROI at C:\\secret\\work\\input.tif\nstack detail", false),
            new MockHttpServletRequest("POST", "/api/multisource/processing-tasks")
        );

        assertThat(response.getMsg()).isEqualTo("Worker 执行失败，请稍后重试");
        assertThat(response.getMsg()).doesNotContain("C:\\secret");
        assertThat(response.getMsg()).doesNotContain("stack detail");
    }

    @Test
    void returnsActionableArchiveUploadCompletionErrors() {
        MultisourceExceptionHandler handler = new MultisourceExceptionHandler();
        HttpServletRequest request = new MockHttpServletRequest(
            "POST", "/api/multisource/archive-upload/sessions/upload-01/complete"
        );

        assertThat(handler.handleIllegalState(
            new IllegalStateException("仍有分片未上传"), request
        ).getMsg()).isEqualTo("ZIP 上传不完整，请重新上传");
        assertThat(handler.handleIllegalState(
            new IllegalStateException("分片合并失败"), request
        ).getMsg()).isEqualTo("ZIP 分片合并失败，请重新上传");
        assertThat(handler.handleIllegalState(
            new IllegalStateException("解析任务繁忙，请稍后重试"), request
        ).getMsg()).isEqualTo("解析任务繁忙，请稍后重试");
    }

    @Test
    void keepsUnknownArchiveStateErrorsGeneric() {
        MultisourceExceptionHandler handler = new MultisourceExceptionHandler();

        R<Void> response = handler.handleIllegalState(
            new IllegalStateException("merge path C:\\secret\\archive.zip failed"),
            new MockHttpServletRequest("POST", "/api/multisource/archive-upload/sessions/upload-01/complete")
        );

        assertThat(response.getMsg()).isEqualTo("多源处理状态不可用，请重试");
        assertThat(response.getMsg()).doesNotContain("C:\\secret");
    }
}
