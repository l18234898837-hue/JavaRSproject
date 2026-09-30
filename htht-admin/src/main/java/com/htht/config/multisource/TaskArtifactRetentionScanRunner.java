package com.htht.config.multisource;

import com.htht.multisource.application.usecase.TaskArtifactRetentionScanUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Objects;

/** Explicitly enabled, read-only historical initialization run at application startup. */
public final class TaskArtifactRetentionScanRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskArtifactRetentionScanRunner.class);
    private final TaskArtifactRetentionScanUseCase scanUseCase;

    public TaskArtifactRetentionScanRunner(TaskArtifactRetentionScanUseCase scanUseCase) {
        this.scanUseCase = Objects.requireNonNull(scanUseCase, "scanUseCase");
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = scanUseCase.scan();
        log.info("历史任务产物状态初始化完成: available={}, missing={}, skipped={}, unreadable={}",
            result.createdAvailable(), result.createdMissing(), result.skippedExisting(), result.unreadableMetadata());
    }
}
