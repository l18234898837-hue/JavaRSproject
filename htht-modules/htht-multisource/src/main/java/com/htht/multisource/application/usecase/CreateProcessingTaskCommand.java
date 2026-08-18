package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.StorageUri;

import java.util.List;

public record CreateProcessingTaskCommand(
    String name,
    AccessSubject subject,
    List<InputAsset> inputs,
    String parameterSnapshot,
    StorageUri outputUri
) {
    public CreateProcessingTaskCommand {
        inputs = List.copyOf(inputs);
    }
}
