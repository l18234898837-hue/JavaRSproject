package com.htht.multisource.domain.model;

import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.application.port.out.model.StageOutput;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ProcessingPlanValidatorTest {

    @Test
    void acceptValidDag() {
        ProcessingStage read = new ProcessingStage("READ", "READ_SOURCE", List.of());
        ProcessingStage calibrate = new ProcessingStage("CAL", "CALIBRATE", List.of("READ"));
        ProcessingStage project = new ProcessingStage("PROJECT", "PROJECT", List.of("CAL"));

        ProcessingPlan plan = new ProcessingPlan("v1", List.of(read, calibrate, project));

        assertThat(plan.stages()).containsExactly(read, calibrate, project);
    }

    @Test
    void rejectPlanWithCycle() {
        ProcessingStage a = new ProcessingStage("A", "READ", List.of("B"));
        ProcessingStage b = new ProcessingStage("B", "CAL", List.of("A"));

        assertThatThrownBy(() -> new ProcessingPlan("v1", List.of(a, b)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectPlanWithUnknownDependencyOrDuplicateStageKey() {
        ProcessingStage read = new ProcessingStage("READ", "READ_SOURCE", List.of());
        ProcessingStage duplicateRead = new ProcessingStage("READ", "READ_AGAIN", List.of());
        ProcessingStage calibrate = new ProcessingStage("CAL", "CALIBRATE", List.of("MISSING"));

        assertThatThrownBy(() -> new ProcessingPlan("v1", List.of(read, calibrate)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProcessingPlan("v1", List.of(read, duplicateRead)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stageRequiresDependencyList() {
        assertThatThrownBy(() -> new ProcessingStage("READ", "READ_SOURCE", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("dependsOnStageKeys");
    }

    @Test
    void rejectsUpstreamArtifactWithoutProducingDependency() {
        ProcessingStage write = stage(
            "WRITE", List.of(),
            List.of(new StageInput("decoded", StageInput.Source.UPSTREAM_ARTIFACT, "DECODED_CZI_RASTER", true)),
            List.of(finalOutput("STANDARD_COG", "standard.tif"))
        );

        assertThatThrownBy(() -> new ProcessingPlan("plan-v2", "rules-2026.08", List.of(write)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("upstream artifact");
    }

    @Test
    void rejectsDuplicateInputKeysAndUnsafeOutputPaths() {
        ProcessingStage duplicateInputs = stage(
            "DECODE", List.of(),
            List.of(
                new StageInput("main", StageInput.Source.RAW_INPUT, "MAIN", true),
                new StageInput("main", StageInput.Source.RAW_INPUT, "METADATA", false)
            ),
            List.of(intermediateOutput("DECODED_CZI_RASTER", "decoded.npz"))
        );
        assertThatThrownBy(() -> new ProcessingPlan("plan-v2", "rules-2026.08", List.of(duplicateInputs)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate input");
        assertThatThrownBy(() -> new StageOutput("STANDARD_COG", "../standard.tif", StageOutput.Retention.FINAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("relative path");
    }

    @Test
    void rejectsDuplicateProducedArtifactsAndConsumedFinalOutputs() {
        ProcessingStage decode = stage(
            "DECODE", List.of(), List.of(),
            List.of(
                intermediateOutput("DECODED_CZI_RASTER", "decoded.npz"),
                intermediateOutput("DECODED_CZI_RASTER", "decoded-copy.npz")
            )
        );
        ProcessingStage finalProducer = stage(
            "FINAL", List.of(), List.of(),
            List.of(finalOutput("STANDARD_COG", "standard.tif"))
        );
        ProcessingStage consumer = stage(
            "CONSUME", List.of("FINAL"),
            List.of(new StageInput("standard", StageInput.Source.UPSTREAM_ARTIFACT, "STANDARD_COG", true)),
            List.of(intermediateOutput("COPY", "copy.npz"))
        );

        assertThatThrownBy(() -> new ProcessingPlan("plan-v2", "rules-2026.08", List.of(decode)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate output");
        assertThatThrownBy(() -> new ProcessingPlan("plan-v2", "rules-2026.08", List.of(finalProducer, consumer)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("final artifact");
    }

    private static ProcessingStage stage(
        String key,
        List<String> dependencies,
        List<StageInput> inputs,
        List<StageOutput> outputs
    ) {
        return new ProcessingStage(key, key + "_CODE", dependencies, inputs, outputs, "{}");
    }

    private static StageOutput intermediateOutput(String artifactType, String relativePath) {
        return new StageOutput(artifactType, relativePath, StageOutput.Retention.INTERMEDIATE);
    }

    private static StageOutput finalOutput(String artifactType, String relativePath) {
        return new StageOutput(artifactType, relativePath, StageOutput.Retention.FINAL);
    }
}
