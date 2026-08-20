package com.htht.config.multisource;

import com.htht.multisource.infrastructure.worker.LocalProcessWorkerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("multisource.worker")
public class MultiSourceWorkerProperties {
    private List<String> command = new ArrayList<>();
    private Path workRoot;
    private Duration timeout;

    public List<String> getCommand() {
        return command;
    }

    public void setCommand(List<String> command) {
        this.command = command;
    }

    public Path getWorkRoot() {
        return workRoot;
    }

    public void setWorkRoot(Path workRoot) {
        this.workRoot = workRoot;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public LocalProcessWorkerProperties toLocalProcessProperties() {
        if (command == null || command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("multisource.worker.command must be configured through MULTISOURCE_WORKER_COMMAND.");
        }
        if (workRoot == null) {
            throw new IllegalStateException("multisource.worker.work-root must be configured through MULTISOURCE_WORK_ROOT.");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("multisource.worker.timeout must be configured through MULTISOURCE_WORKER_TIMEOUT as a positive duration.");
        }
        return new LocalProcessWorkerProperties(command, workRoot, timeout);
    }
}
