package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.application.usecase.service.StageInputResolver;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.Artifact;
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
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.TaskStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class StageInputResolverTest {

    private static final TaskId TASK_ID = TaskId.of(101L);

    private final StageInputResolver resolver = new StageInputResolver();

    @Test
    void resolvesRawInputByDeclaredArtifactRoleAndPreservesItsReferenceAndOrdinal() {
        ProcessingStage read = new ProcessingStage("READ", "READ_SOURCE", List.of(), List.of(
            new StageInput("source", StageInput.Source.RAW_INPUT, "MAIN", true)
        ), List.of(), "{}");
        InputAsset main = input("MAIN", 3, "main.nc");
        ProcessingTask task = task(List.of(read), List.of(main));

        List<InputAsset> resolved = resolver.resolve(task, read, List.of(stage(201L, "READ")), List.of());

        assertThat(resolved).containsExactly(main);
    }

    @Test
    void resolvesOnlyTheDeclaredFy3hObservationAndGeo1kInputs() {
        ProcessingStage decode = new ProcessingStage("DECODE_MERSI_L1", "DECODE_MERSI_L1", List.of(), List.of(
            new StageInput("observation", StageInput.Source.RAW_INPUT, "OBSERVATION", true),
            new StageInput("geo", StageInput.Source.RAW_INPUT, "GEO1K", true)
        ), List.of(output("DECODED_MERSI_L1")), "{}");
        InputAsset observation = input("OBSERVATION", 0, "observation.HDF");
        InputAsset geo = input("GEO1K", 1, "geo.HDF");

        assertThat(resolver.resolve(task(List.of(decode), List.of(observation, geo, input("UNRELATED", 2, "extra.bin"))), decode,
            List.of(stage(201L, "DECODE_MERSI_L1")), List.of())).containsExactly(observation, geo);
    }

    @Test
    void ignoresAnOptionalInputWhenNoMatchingAssetExists() {
        ProcessingStage read = new ProcessingStage("READ", "READ_SOURCE", List.of(), List.of(
            new StageInput("metadata", StageInput.Source.RAW_INPUT, "METADATA", false)
        ), List.of(), "{}");

        List<InputAsset> resolved = resolver.resolve(task(List.of(read), List.of(input("MAIN", 0, "main.nc"))), read,
            List.of(stage(201L, "READ")), List.of());

        assertThat(resolved).isEmpty();
    }

    @Test
    void rejectsRequiredInputWhenNoMatchingAssetExists() {
        ProcessingStage read = new ProcessingStage("READ", "READ_SOURCE", List.of(), List.of(
            new StageInput("source", StageInput.Source.RAW_INPUT, "MAIN", true)
        ), List.of(), "{}");

        assertThatThrownBy(() -> resolver.resolve(task(List.of(read), List.of(input("METADATA", 0, "meta.xml"))), read,
            List.of(stage(201L, "READ")), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("source")
            .hasMessageContaining("exactly one");
    }

    @Test
    void rejectsMultipleMatchesEvenWhenInputIsOptional() {
        ProcessingStage read = new ProcessingStage("READ", "READ_SOURCE", List.of(), List.of(
            new StageInput("metadata", StageInput.Source.RAW_INPUT, "METADATA", false)
        ), List.of(), "{}");

        assertThatThrownBy(() -> resolver.resolve(task(List.of(read), List.of(
            input("METADATA", 0, "first.xml"), input("METADATA", 1, "second.xml")
        )), read, List.of(stage(201L, "READ")), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("metadata")
            .hasMessageContaining("exactly one");
    }

    @Test
    void rejectsUpstreamArtifactProducedByAnUndeclaredDependency() {
        ProcessingStage read = new ProcessingStage("READ", "READ_SOURCE", List.of(), List.of(), List.of(
            output("DECODED")
        ), "{}");
        ProcessingStage other = new ProcessingStage("OTHER", "OTHER_SOURCE", List.of(), List.of(), List.of(
            output("OTHER")
        ), "{}");
        ProcessingStage apply = new ProcessingStage("APPLY", "APPLY_FLAGS", List.of("READ"), List.of(
            new StageInput("decoded", StageInput.Source.UPSTREAM_ARTIFACT, "DECODED", true)
        ), List.of(), "{}");
        ProcessingTask task = task(List.of(read, other, apply), List.of(input("MAIN", 0, "main.nc")));
        TaskStage readStage = stage(201L, "READ");
        TaskStage otherStage = stage(202L, "OTHER");
        TaskStage applyStage = stage(203L, "APPLY");
        Artifact artifact = new Artifact(TASK_ID, otherStage.id(), "DECODED", new StorageUri("rs://artifact/other.nc"),
            "sha256:other", 10L);

        assertThatThrownBy(() -> resolver.resolve(task, apply, List.of(readStage, otherStage, applyStage), List.of(artifact)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("declared dependency");
    }

    @Test
    void convertsADeclaredUpstreamArtifactIntoAStageKeyedTaskArtifactInput() {
        ProcessingStage read = new ProcessingStage("READ", "READ_SOURCE", List.of(), List.of(), List.of(
            output("DECODED")
        ), "{}");
        ProcessingStage apply = new ProcessingStage("APPLY", "APPLY_FLAGS", List.of("READ"), List.of(
            new StageInput("decoded", StageInput.Source.UPSTREAM_ARTIFACT, "DECODED", true)
        ), List.of(), "{}");
        ProcessingTask task = task(List.of(read, apply), List.of(input("MAIN", 0, "main.nc")));
        TaskStage readStage = stage(201L, "READ");
        Artifact artifact = new Artifact(TASK_ID, readStage.id(), "DECODED", new StorageUri("rs://artifact/decoded.nc"),
            "sha256:decoded", 10L);

        List<InputAsset> resolved = resolver.resolve(task, apply, List.of(readStage, stage(202L, "APPLY")), List.of(artifact));

        assertThat(resolved).containsExactly(new InputAsset("decoded", 0,
            new AssetReference("TASK_ARTIFACT", "rs://artifact/decoded.nc"),
            new StorageUri("rs://task-artifact/legacy/101/201"), "sha256:decoded", true));
    }

    private static ProcessingTask task(List<ProcessingStage> stages, List<InputAsset> assets) {
        ProductCandidate candidate = new ProductCandidate("package-01", new ProductProfileKey("HY-2D", "v1"),
            ProductDataShape.ALONG_TRACK_OBSERVATION, ProductSupportStatus.SUPPORTED, assets, "{}", 0.91);
        return new ProcessingTask(TASK_ID, "task-01", new AccessSubject(7L), TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED, "{}", new StorageUri("rs://output/task-01"), new ProcessingPlan("plan-v1", stages),
            new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate)), List.of(candidate));
    }

    private static InputAsset input(String role, int ordinal, String filename) {
        return new InputAsset(role, ordinal, new AssetReference("SYS_FILE", filename), new StorageUri("rs://input/" + filename),
            "sha256:" + filename, true);
    }

    private static TaskStage stage(long id, String key) {
        return new TaskStage(new StageId(id), TASK_ID, key, StageExecutionStatus.PENDING, 1);
    }

    private static StageOutput output(String type) {
        return new StageOutput(type, type.toLowerCase() + ".bin", StageOutput.Retention.INTERMEDIATE);
    }
}
