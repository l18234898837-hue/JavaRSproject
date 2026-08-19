package com.htht.multisource.domain.model;

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
}
