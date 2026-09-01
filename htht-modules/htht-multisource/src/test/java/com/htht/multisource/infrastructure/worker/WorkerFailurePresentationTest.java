package com.htht.multisource.infrastructure.worker;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class WorkerFailurePresentationTest {

    @Test
    void mapsStageTimeoutToAFixedSafeMessage() {
        WorkerFailurePresentation.SafeFailure failure = WorkerFailurePresentation.forClient(
            WorkerFailurePresentation.WORKER_STAGE_TIMEOUT,
            "internal timeout detail C:\\secret\\worker-output.tif"
        );

        assertThat(failure.code()).isEqualTo(WorkerFailurePresentation.WORKER_STAGE_TIMEOUT);
        assertThat(failure.message()).isEqualTo(WorkerFailurePresentation.WORKER_STAGE_TIMEOUT_MESSAGE);
        assertThat(failure.message()).doesNotContain("C:\\secret");
    }

    @Test
    void continuesToHideUnknownWorkerFailures() {
        WorkerFailurePresentation.SafeFailure failure = WorkerFailurePresentation.forClient(
            "UNKNOWN_FAILURE", "failed to read C:\\secret\\worker-output.tif"
        );

        assertThat(failure.code()).isEqualTo(WorkerFailurePresentation.WORKER_DISPATCH_FAILED);
        assertThat(failure.message()).isEqualTo(WorkerFailurePresentation.GENERIC_WORKER_FAILURE_MESSAGE);
    }
}
