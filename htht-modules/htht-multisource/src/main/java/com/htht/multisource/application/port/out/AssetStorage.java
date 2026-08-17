package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.AssetMetadata;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;

import java.util.List;

public interface AssetStorage {

    AssetAccessGrant grantPreflightRead(AccessSubject subject, List<InputAsset> inputs);

    ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri);

    AssetMetadata resolve(AccessSubject subject, AssetReference reference);
}
