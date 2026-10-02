package com.htht.multisource.application.port.out;

import java.nio.file.Path;
import java.util.Optional;

public interface ManagedInputFileRepository {
    long findOrRegister(String workspaceKey, long sysFileId, String path, String checksum, long sizeBytes);

    /** Optional cleanup hooks; legacy adapters remain source-compatible. */
    default Optional<ManagedInputFile> findById(long managedFileId) { return Optional.empty(); }
    default Optional<ManagedInputFile> findByPath(Path path) { return Optional.empty(); }
    default boolean claimPurge(long managedFileId, long expectedVersion) { return true; }
    default void markPurged(long managedFileId, long expectedVersion) { }
    default void markPurgeFailed(long managedFileId, long expectedVersion, String reason) { }

    record ManagedInputFile(long id, String workspaceKey, long sysFileId, Path path,
                            String checksum, long sizeBytes, String status, long version) { }
}
