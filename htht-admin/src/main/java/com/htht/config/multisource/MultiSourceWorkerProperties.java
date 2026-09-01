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
    private Path artifactRoot;
    private Duration timeout;
    private Duration stageTimeout;
    private String processorVersion;

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

    public Path getArtifactRoot() {
        return artifactRoot;
    }

    public void setArtifactRoot(Path artifactRoot) {
        this.artifactRoot = artifactRoot;
    }

    public Path requireArtifactRoot() {
        if (artifactRoot == null) {
            throw new IllegalStateException("multisource.worker.artifact-root must be configured through MULTISOURCE_ARTIFACT_ROOT.");
        }
        return artifactRoot;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getStageTimeout() {
        return stageTimeout;
    }

    public void setStageTimeout(Duration stageTimeout) {
        this.stageTimeout = stageTimeout;
    }

    public String getProcessorVersion() {
        return processorVersion;
    }

    public void setProcessorVersion(String processorVersion) {
        this.processorVersion = processorVersion;
    }

    public String requireProcessorVersion() {
        if (processorVersion == null || processorVersion.isBlank()) {
            throw new IllegalStateException("multisource.worker.processor-version must be configured through MULTISOURCE_WORKER_PROCESSOR_VERSION.");
        }
        return processorVersion;
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
        Duration resolvedStageTimeout = stageTimeout == null ? timeout : stageTimeout;
        if (resolvedStageTimeout.isZero() || resolvedStageTimeout.isNegative()) {
            throw new IllegalStateException("multisource.worker.stage-timeout must be a positive duration.");
        }
        return new LocalProcessWorkerProperties(command, workRoot, timeout, resolvedStageTimeout);
    }
}
