package com.htht.multisource.infrastructure.worker;

import java.util.Arrays;

public enum WorkerOperation {

    INSPECT("inspect"),
    INSPECT_ARTIFACT("inspect-artifact"),
    INSPECT_RASTER_ARTIFACT("inspect-raster-artifact"),
    RENDER_ARTIFACT_TILE("render-artifact-tile"),
    BUILD_PLAN("build-plan"),
    EXECUTE_STAGE("execute-stage");

    private final String wireValue;

    WorkerOperation(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static WorkerOperation fromWireValue(String wireValue) {
        return Arrays.stream(values())
            .filter(operation -> operation.wireValue.equals(wireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported Worker operation: " + wireValue));
    }
}
