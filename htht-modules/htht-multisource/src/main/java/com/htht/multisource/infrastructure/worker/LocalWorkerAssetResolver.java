package com.htht.multisource.infrastructure.worker;

import com.htht.multisource.domain.model.StorageUri;

import java.nio.file.Path;

@FunctionalInterface
public interface LocalWorkerAssetResolver {
    Path resolve(String grantReference, StorageUri storageUri);

    /**
     * Returns the user-visible filename associated with the authorized asset. The default keeps
     * existing resolver lambdas source-compatible while storage adapters that rename files can
     * supply the persisted original name.
     */
    default String originalFileName(String grantReference, StorageUri storageUri, Path resolvedPath) {
        return resolvedPath == null || resolvedPath.getFileName() == null
            ? null
            : resolvedPath.getFileName().toString();
    }
}
