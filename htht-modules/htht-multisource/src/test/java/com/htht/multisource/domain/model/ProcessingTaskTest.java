package com.htht.multisource.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ProcessingTaskTest {

    @Test
    void taskCanRepresentBatchPlanAndCandidates() {
        ProductCandidate candidate = sampleCandidate("fy3h-mersi-granule-01");
        ProcessingBatch batch = new ProcessingBatch(
            10L,
            "ocean-color-batch",
            ProcessingBatchStatus.CREATED,
            List.of(candidate)
        );
        ProcessingPlan plan = new ProcessingPlan(
            "v1",
            List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))
        );

        ProcessingTask task = new ProcessingTask(
            TaskId.of(1L),
            "ocean-color-task",
            new AccessSubject(7L),
            TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED,
            "{\"mode\":\"standard\"}",
            new StorageUri("rs://output/ocean-color-task"),
            plan,
            batch,
            List.of(candidate)
        );

        assertThat(task.plan()).isEqualTo(plan);
        assertThat(task.batch()).isEqualTo(batch);
        assertThat(task.candidates()).containsExactly(candidate);
        assertThat(task.name()).isEqualTo("ocean-color-task");
        assertThat(task.parameterSnapshot()).isEqualTo("{\"mode\":\"standard\"}");
        assertThat(task.outputUri()).isEqualTo(new StorageUri("rs://output/ocean-color-task"));
        assertThat(candidate.files()).singleElement().satisfies(asset ->
            assertThat(asset.storageUriSnapshot()).isEqualTo(new StorageUri("rs://input/fy3h-mersi-granule-01.hdf"))
        );
    }

    @Test
    void taskCopiesCandidates() {
        ProductCandidate candidate = sampleCandidate("fy3h-mersi-granule-01");
        List<ProductCandidate> candidates = new ArrayList<>();
        candidates.add(candidate);

        ProcessingTask task = new ProcessingTask(
            TaskId.of(1L),
            "ocean-color-task",
            new AccessSubject(7L),
            TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED,
            "{\"mode\":\"standard\"}",
            new StorageUri("rs://output/ocean-color-task"),
            new ProcessingPlan("v1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))),
            sampleBatch(candidate),
            candidates
        );
        candidates.clear();

        assertThat(task.candidates()).containsExactly(candidate);
        assertThatThrownBy(() -> task.candidates().add(candidate))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void taskRequiresBatch() {
        ProductCandidate candidate = sampleCandidate("fy3h-mersi-granule-01");

        assertThatThrownBy(() -> new ProcessingTask(
            TaskId.of(1L),
            "ocean-color-task",
            new AccessSubject(7L),
            TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED,
            "{\"mode\":\"standard\"}",
            new StorageUri("rs://output/ocean-color-task"),
            new ProcessingPlan("v1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))),
            null,
            List.of(candidate)
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void taskRejectsIncompleteCreationSnapshots() {
        ProductCandidate candidate = sampleCandidate("fy3h-mersi-granule-01");
        ProcessingPlan plan = new ProcessingPlan("v1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of())));
        ProcessingBatch batch = sampleBatch(candidate);

        assertThatThrownBy(() -> new ProcessingTask(
            TaskId.of(1L), " ", new AccessSubject(7L), TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED,
            "{\"mode\":\"standard\"}", new StorageUri("rs://output/ocean-color-task"), plan, batch, List.of(candidate)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProcessingTask(
            TaskId.of(1L), "ocean-color-task", new AccessSubject(7L), TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED, " ", new StorageUri("rs://output/ocean-color-task"), plan, batch, List.of(candidate)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProcessingTask(
            TaskId.of(1L), "ocean-color-task", new AccessSubject(7L), TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED, "{\"mode\":\"standard\"}", null, plan, batch, List.of(candidate)
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void inputAssetRequiresStorageUriSnapshot() {
        assertThatThrownBy(() -> new InputAsset(
            "MAIN", 0, new AssetReference("LOCAL_FILE", "granule.hdf"), null, "sha256:granule", true
        )).isInstanceOf(NullPointerException.class);
    }

    private ProductCandidate sampleCandidate(String packageId) {
        return new ProductCandidate(
            packageId,
            new ProductProfileKey("FY-3H-MERSI", "v1"),
            ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.CANDIDATE,
            List.of(new InputAsset(
                "MAIN",
                0,
                new AssetReference("LOCAL_FILE", packageId + ".hdf"),
                new StorageUri("rs://input/" + packageId + ".hdf"),
                "sha256:" + packageId,
                true
            )),
            0.91
        );
    }

    private ProcessingBatch sampleBatch(ProductCandidate candidate) {
        return new ProcessingBatch(
            10L,
            "ocean-color-batch",
            ProcessingBatchStatus.CREATED,
            List.of(candidate)
        );
    }
}
