package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingBatchStatus;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingTaskEntity;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingBatchEntity;
import com.htht.multisource.infrastructure.persistence.entity.BatchProductEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputAssetEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskProductEntity;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Tag("dev")
class RepositoryMappingTest {

    private final TaskPersistenceMapper mapper = new TaskPersistenceMapper();

    @Test
    void taskRoundTripsItsDurableCreationSnapshots() {
        ProcessingTask task = sampleTask();

        ProcessingTaskEntity entity = mapper.toEntity(task);

        assertThat(entity.getName()).isEqualTo("task-01");
        assertThat(entity.getParameterSnapshot()).isEqualTo("{\"mode\":\"standard\"}");
        assertThat(entity.getOutputUri()).isEqualTo("rs://output/task-01");
        assertThat(mapper.toTask(entity, task.batch(), task.candidates())).isEqualTo(task);
    }

    @Test
    void candidateAndInputAssetsMapToTaskScopedSnapshots() {
        ProcessingTask task = sampleTask();

        TaskProductEntity product = mapper.toProductEntities(task).get(0);
        TaskInputAssetEntity asset = mapper.toInputAssetEntities(task).get(0);

        assertThat(product.getTaskId()).isEqualTo(101L);
        assertThat(product.getPackageId()).isEqualTo("fy3h-granule-01");
        assertThat(product.getProductCode()).isEqualTo("FY-3H-MERSI");
        assertThat(asset.getTaskId()).isEqualTo(101L);
        assertThat(asset.getPackageId()).isEqualTo("fy3h-granule-01");
        assertThat(asset.getStorageUriSnapshot()).isEqualTo("rs://input/fy3h-granule-01.hdf");
        assertThat(mapper.toCandidate(product, List.of(asset))).isEqualTo(task.candidates().get(0));
    }

    @Test
    void candidateAssetsRemainAssociatedWhenCandidatesReuseRoleAndOrdinal() {
        ProcessingTask task = sampleTaskWithSharedAssetCoordinates();

        List<TaskInputAssetEntity> assets = mapper.toInputAssetEntities(task);

        assertThat(assets).extracting(TaskInputAssetEntity::getPackageId)
            .containsExactly("fy3h-granule-01", "hy1c-granule-01");
        assertThat(assets).extracting(TaskInputAssetEntity::getRole, TaskInputAssetEntity::getOrdinal)
            .containsExactly(tuple("MAIN", 0), tuple("MAIN", 0));
        assertThat(mapper.toCandidate(mapper.toProductEntities(task).get(1),
            assets.stream().filter(asset -> asset.getPackageId().equals("hy1c-granule-01")).toList()))
            .isEqualTo(task.candidates().get(1));
    }

    @Test
    void batchPreservesItsIdentifierNameAndStatus() {
        ProcessingBatch batch = sampleTask().batch();

        ProcessingBatchEntity entity = mapper.toBatchEntity(batch);

        assertThat(entity.getBatchId()).isEqualTo(11L);
        assertThat(entity.getName()).isEqualTo("batch-01");
        assertThat(entity.getStatus()).isEqualTo("CREATED");
    }

    @Test
    void batchCandidatePreservesInputAssetSnapshotWithoutChildTask() {
        ProductCandidate candidate = sampleTask().candidates().get(0);

        BatchProductEntity entity = mapper.toBatchProductEntity(11L, candidate);

        assertThat(entity.getBatchId()).isEqualTo(11L);
        assertThat(entity.getInputAssetsSnapshot()).contains("rs://input/fy3h-granule-01.hdf");
        assertThat(mapper.toBatchCandidate(entity)).isEqualTo(candidate);
    }

    @Test
    void stageAndArtifactRetainExecutionIdentifiersAndStorage() {
        TaskStage stage = new TaskStage(new StageId(201L), TaskId.of(101L), "READ", StageExecutionStatus.PENDING, 1);
        Artifact artifact = new Artifact(TaskId.of(101L), new StageId(201L), "OUTPUT", new StorageUri("rs://output/a.tif"), "sha256:a");

        assertThat(mapper.toStageEntity(stage).getStageKey()).isEqualTo("READ");
        assertThat(mapper.toArtifactEntity(artifact).getStorageUri()).isEqualTo("rs://output/a.tif");
    }

    private static ProcessingTask sampleTask() {
        ProductCandidate candidate = new ProductCandidate(
            "fy3h-granule-01",
            new ProductProfileKey("FY-3H-MERSI", "v1"),
            ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.SUPPORTED,
            List.of(new InputAsset(
                "MAIN", 0, new AssetReference("LOCAL_FILE", "fy3h-granule-01.hdf"),
                new StorageUri("rs://input/fy3h-granule-01.hdf"), "sha256:fy3h-granule-01", true
            )),
            0.91
        );
        ProcessingBatch batch = new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate));
        ProcessingPlan plan = new ProcessingPlan("plan-v1", List.of(
            new ProcessingStage("READ", "READ_SOURCE", List.of()),
            new ProcessingStage("CAL", "CALIBRATE", List.of("READ"))
        ));
        return new ProcessingTask(
            TaskId.of(101L), "task-01", new AccessSubject(7L), TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED, "{\"mode\":\"standard\"}", new StorageUri("rs://output/task-01"),
            plan, batch, List.of(candidate)
        );
    }

    private static ProcessingTask sampleTaskWithSharedAssetCoordinates() {
        ProcessingTask task = sampleTask();
        ProductCandidate secondCandidate = new ProductCandidate(
            "hy1c-granule-01",
            new ProductProfileKey("HY-1C-CZI", "v1"),
            ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.SUPPORTED,
            List.of(new InputAsset(
                "MAIN", 0, new AssetReference("LOCAL_FILE", "hy1c-granule-01.hdf"),
                new StorageUri("rs://input/hy1c-granule-01.hdf"), "sha256:hy1c-granule-01", true
            )),
            0.88
        );
        ProcessingBatch batch = new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED,
            List.of(task.candidates().get(0), secondCandidate));
        return new ProcessingTask(
            task.id(), task.name(), task.creator(), task.lifecycleStatus(), task.qualityStatus(),
            task.parameterSnapshot(), task.outputUri(), task.plan(), batch, batch.candidates()
        );
    }
}
