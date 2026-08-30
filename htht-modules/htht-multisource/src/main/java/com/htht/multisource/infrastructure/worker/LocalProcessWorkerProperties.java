package com.htht.multisource.infrastructure.worker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record LocalProcessWorkerProperties(
    List<String> command,
    Path workRoot,
    Duration timeout,
    Duration stageTimeout
) {

    public LocalProcessWorkerProperties(List<String> command, Path workRoot, Duration timeout) {
        this(command, workRoot, timeout, timeout);
    }

    public LocalProcessWorkerProperties {
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Worker command must not be empty.");
        }
        workRoot = Objects.requireNonNull(workRoot, "workRoot").toAbsolutePath().normalize();
        timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Worker timeout must be positive.");
        }
        stageTimeout = Objects.requireNonNull(stageTimeout, "stageTimeout");
        if (stageTimeout.isNegative() || stageTimeout.isZero()) {
            throw new IllegalArgumentException("Worker stage timeout must be positive.");
        }
    }

    public Duration timeoutFor(WorkerOperation operation) {
        return operation == WorkerOperation.EXECUTE_STAGE ? stageTimeout : timeout;
    }
}
