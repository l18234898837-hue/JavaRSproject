package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.TaskStage;

import java.util.Objects;

public record StageClaim(TaskStage stage) {

    public StageClaim {
        Objects.requireNonNull(stage, "stage");
    }
}
