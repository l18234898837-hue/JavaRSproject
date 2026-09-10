package com.htht.config.multisource;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.TaskArtifactStore;
import com.htht.multisource.application.usecase.CreateProcessingBatchUseCase;
import com.htht.multisource.application.usecase.CreateProcessingTaskUseCase;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.PreflightProductUseCase;
import com.htht.multisource.infrastructure.persistence.mapper.ArtifactMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.BatchProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingBatchMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingTaskMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskInputAssetMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskEventMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskStageMyBatisMapper;
import com.htht.system.service.ISysFileService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("dev")
class MultiSourceAdapterConfigurationTest {

    @TempDir
    Path uploadRoot;

    @Test
    void assemblesSliceAWithEnvironmentBoundWorkerSettings() {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AssetStorage.class)).isInstanceOf(CompositeLocalWorkerAssetResolver.class);
            assertThat(context).hasSingleBean(TaskArtifactStore.class);
            assertThat(context).hasSingleBean(PreflightProductUseCase.class);
            assertThat(context).hasSingleBean(CreateProcessingTaskUseCase.class);
            assertThat(context).hasSingleBean(CreateProcessingBatchUseCase.class);
            assertThat(context).hasSingleBean(GetTaskDetailUseCase.class);
            assertThat(context).hasSingleBean(MultiSourceWorkerProperties.class);
            assertThat(context).hasBean("multisourceStageExecutor");
            MultiSourceWorkerProperties properties = context.getBean(MultiSourceWorkerProperties.class);
            assertThat(properties.toLocalProcessProperties().timeout()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.toLocalProcessProperties().stageTimeout()).isEqualTo(Duration.ofHours(12));
        });
    }

    @Test
    void failsStartupWithClearMessageWhenWorkerEnvironmentSettingsAreMissing() {
        new ApplicationContextRunner()
            .withUserConfiguration(MultiSourceAdapterConfiguration.class)
            .withPropertyValues("file.upload-path=" + uploadRoot)
            .withBean(ISysFileService.class, () -> mock(ISysFileService.class))
            .withBean(ProcessingTaskMyBatisMapper.class, () -> mock(ProcessingTaskMyBatisMapper.class))
            .withBean(TaskProductMyBatisMapper.class, () -> mock(TaskProductMyBatisMapper.class))
            .withBean(TaskInputAssetMyBatisMapper.class, () -> mock(TaskInputAssetMyBatisMapper.class))
            .withBean(TaskEventMyBatisMapper.class, () -> mock(TaskEventMyBatisMapper.class))
            .withBean(ProcessingBatchMyBatisMapper.class, () -> mock(ProcessingBatchMyBatisMapper.class))
            .withBean(BatchProductMyBatisMapper.class, () -> mock(BatchProductMyBatisMapper.class))
            .withBean(TaskStageMyBatisMapper.class, () -> mock(TaskStageMyBatisMapper.class))
            .withBean(ArtifactMyBatisMapper.class, () -> mock(ArtifactMyBatisMapper.class))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("multisource.worker.command");
            });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
            .withUserConfiguration(MultiSourceAdapterConfiguration.class)
            .withPropertyValues(
                "file.upload-path=" + uploadRoot,
                "multisource.worker.command=python,worker.py",
                "multisource.worker.work-root=" + uploadRoot.resolve("worker"),
                "multisource.worker.artifact-root=" + uploadRoot.resolve("artifacts"),
                "multisource.worker.timeout=10m",
                "multisource.worker.stage-timeout=12h",
                "multisource.worker.processor-version=worker-test-1.0.0"
            )
            .withBean(ISysFileService.class, () -> mock(ISysFileService.class))
            .withBean(ProcessingTaskMyBatisMapper.class, () -> mock(ProcessingTaskMyBatisMapper.class))
            .withBean(TaskProductMyBatisMapper.class, () -> mock(TaskProductMyBatisMapper.class))
            .withBean(TaskInputAssetMyBatisMapper.class, () -> mock(TaskInputAssetMyBatisMapper.class))
            .withBean(TaskEventMyBatisMapper.class, () -> mock(TaskEventMyBatisMapper.class))
            .withBean(ProcessingBatchMyBatisMapper.class, () -> mock(ProcessingBatchMyBatisMapper.class))
            .withBean(BatchProductMyBatisMapper.class, () -> mock(BatchProductMyBatisMapper.class))
            .withBean(TaskStageMyBatisMapper.class, () -> mock(TaskStageMyBatisMapper.class))
            .withBean(ArtifactMyBatisMapper.class, () -> mock(ArtifactMyBatisMapper.class));
    }
}
