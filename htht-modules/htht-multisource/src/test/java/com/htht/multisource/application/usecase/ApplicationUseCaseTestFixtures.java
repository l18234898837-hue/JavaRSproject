package com.htht.multisource.application.usecase;

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
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.StorageUri;

import java.util.List;

final class ApplicationUseCaseTestFixtures {

    private ApplicationUseCaseTestFixtures() {
    }

    static AccessSubject subject() {
        return new AccessSubject(7L);
    }

    static ProductCandidate candidate(String packageId, ProductSupportStatus supportStatus) {
        ProductProfileKey key = new ProductProfileKey("FY-3H-MERSI", "v1");
        InputAsset asset = new InputAsset(
            "MAIN", 0, new AssetReference("LOCAL_FILE", packageId + ".hdf"),
            new StorageUri("rs://input/" + packageId + ".hdf"),
            "sha256:" + packageId, true
        );
        return new ProductCandidate(
            packageId, key, ProductDataShape.SWATH_RASTER, supportStatus, List.of(asset), 0.91
        );
    }

    static ProcessingPlan samplePlan() {
        return new ProcessingPlan("plan-v1", List.of(
            new ProcessingStage("READ", "READ_SOURCE", List.of()),
            new ProcessingStage("CAL", "CALIBRATE", List.of("READ"))
        ));
    }

    static ProcessingTask task(TaskId taskId, AccessSubject creator) {
        ProductCandidate candidate = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
        ProcessingBatch batch = new ProcessingBatch(
            11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate)
        );
        return new ProcessingTask(
            taskId, "task-01", creator, TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED,
            "{\"mode\":\"standard\"}", new StorageUri("rs://output/task-01"),
            samplePlan(), batch, List.of(candidate)
        );
    }
}
