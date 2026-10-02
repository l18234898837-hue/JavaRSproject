package com.htht.multisource.application.port.out;

public interface ManagedInputFileRepository {
    long findOrRegister(String workspaceKey, long sysFileId, String path, String checksum, long sizeBytes);
}
