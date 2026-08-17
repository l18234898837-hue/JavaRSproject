package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.StorageUri;

public record AssetMetadata(StorageUri storageUri, long size, String checksum) {
}
