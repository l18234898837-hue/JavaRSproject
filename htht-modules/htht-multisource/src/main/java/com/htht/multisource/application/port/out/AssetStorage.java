package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.AssetMetadata;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrantRequest;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;

import java.util.List;

public interface AssetStorage {

    /** Canonical metadata used when registering a managed source input. */
    default ManagedInputFileMetadata managedInputFile(AccessSubject subject, AssetReference reference) {
        throw new UnsupportedOperationException("Managed input metadata is not supported by this storage.");
    }

    record ManagedInputFileMetadata(String path, long sizeBytes, String checksum) {
        public ManagedInputFileMetadata {
            if (path == null || path.isBlank() || sizeBytes < 0 || checksum == null || checksum.isBlank()) {
                throw new IllegalArgumentException("Managed input metadata is invalid.");
            }
        }
    }

    AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs);

    default ExecutionGrant grant(ExecutionGrantRequest request) {
        return grant(request.taskId(), request.inputs(), request.outputUri());
    }

    @Deprecated
    ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri);

    AssetMetadata resolve(AccessSubject subject, AssetReference reference);
}
