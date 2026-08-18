package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.TaskId;

import java.util.List;

public record PlanRequest(
    TaskId taskId,
    InspectionResult inspection,
    List<InputAsset> inputs,
    String parameterSnapshot,
    AssetAccessGrant readGrant
) {
    public PlanRequest {
        inputs = List.copyOf(inputs);
    }
}
