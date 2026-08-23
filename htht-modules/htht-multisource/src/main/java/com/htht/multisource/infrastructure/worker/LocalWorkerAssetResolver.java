package com.htht.multisource.infrastructure.worker;

import com.htht.multisource.domain.model.StorageUri;

import java.nio.file.Path;

@FunctionalInterface
public interface LocalWorkerAssetResolver {
    Path resolve(String grantReference, StorageUri storageUri);
}
