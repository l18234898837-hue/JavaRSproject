package com.htht.multisource.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TaskStageTransitionTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-21T08:00:00Z");
    private static final Instant LEASE_EXPIRES_AT = Instant.parse("2026-08-21T08:05:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-08-21T08:01:00Z");

    @Test
    void startsPendingStageWithoutChangingAttempt() {
        TaskStage running = pendingStage().start(new ExecutionId("exec-01"), "worker-local", STARTED_AT, LEASE_EXPIRES_AT);

        assertThat(running.status()).isEqualTo(StageExecutionStatus.RUNNING);
        assertThat(running.attempt()).isEqualTo(1);
        assertThat(running.execution().executionId().value()).isEqualTo("exec-01");
        assertThat(running.execution().startedAt()).isEqualTo(STARTED_AT);
        assertThat(running.execution().leaseExpiresAt()).isEqualTo(LEASE_EXPIRES_AT);
    }

    @Test
    void completesRunningStageForCurrentExecutionAndAttempt() {
        TaskStage succeeded = runningStage().succeed(new ExecutionId("exec-01"), 1, FINISHED_AT);

        assertThat(succeeded.status()).isEqualTo(StageExecutionStatus.SUCCEEDED);
        assertThat(succeeded.execution().finishedAt()).isEqualTo(FINISHED_AT);
    }

    @Test
    void failsRunningStageForCurrentExecutionAndAttempt() {
        TaskStage failed = runningStage().fail(new ExecutionId("exec-01"), 1, "WORKER_ERROR", "processor failed", FINISHED_AT);

        assertThat(failed.status()).isEqualTo(StageExecutionStatus.FAILED);
        assertThat(failed.execution().errorCode()).isEqualTo("WORKER_ERROR");
        assertThat(failed.execution().errorMessage()).isEqualTo("processor failed");
    }

    @Test
    void movesExpiredRunningStageToRecoveryPending() {
        TaskStage recovering = runningStage().recover(new ExecutionId("exec-01"), 1, FINISHED_AT);

        assertThat(recovering.status()).isEqualTo(StageExecutionStatus.RECOVERY_PENDING);
        assertThat(recovering.execution().finishedAt()).isEqualTo(FINISHED_AT);
    }

    @Test
    void retriesRecoveredStageByIncrementingAttemptAndClearingExecution() {
        TaskStage retrying = runningStage().recover(new ExecutionId("exec-01"), 1, FINISHED_AT).retry();

        assertThat(retrying.status()).isEqualTo(StageExecutionStatus.RETRYING);
        assertThat(retrying.attempt()).isEqualTo(2);
        assertThat(retrying.execution()).isNull();
    }

    @Test
    void startsRetryingStageWithNewExecution() {
        TaskStage running = runningStage().recover(new ExecutionId("exec-01"), 1, FINISHED_AT)
            .retry().start(new ExecutionId("exec-02"), "worker-local", FINISHED_AT, LEASE_EXPIRES_AT.plusSeconds(60));

        assertThat(running.status()).isEqualTo(StageExecutionStatus.RUNNING);
        assertThat(running.attempt()).isEqualTo(2);
        assertThat(running.execution().executionId().value()).isEqualTo("exec-02");
    }

    @Test
    void rejectsTerminalAndStaleTransitions() {
        assertThatThrownBy(() -> runningStage().succeed(new ExecutionId("wrong"), 1, FINISHED_AT))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runningStage().fail(new ExecutionId("exec-01"), 2, "ERROR", "stale", FINISHED_AT))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runningStage().succeed(new ExecutionId("exec-01"), 1, FINISHED_AT)
            .fail(new ExecutionId("exec-01"), 1, "ERROR", "late", FINISHED_AT))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidAttemptAndAllowsWorkerIdToArriveAfterClaim() {
        assertThatThrownBy(() -> new TaskStage(new StageId(1L), TaskId.of(2L), "READ", StageExecutionStatus.PENDING, 0))
            .isInstanceOf(IllegalArgumentException.class);

        TaskStage running = pendingStage().start(new ExecutionId("exec-01"), null, STARTED_AT, LEASE_EXPIRES_AT);

        assertThat(running.execution().workerId()).isNull();
    }

    private static TaskStage pendingStage() {
        return new TaskStage(new StageId(201L), TaskId.of(101L), "READ", StageExecutionStatus.PENDING, 1);
    }

    private static TaskStage runningStage() {
        return pendingStage().start(new ExecutionId("exec-01"), "worker-local", STARTED_AT, LEASE_EXPIRES_AT);
    }
}
