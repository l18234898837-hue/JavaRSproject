package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.infrastructure.persistence.mapper.ManagedInputFileMyBatisMapper;
import java.util.Objects;
import java.util.Optional;
import java.nio.file.Path;

public final class MyBatisManagedInputFileRepository implements ManagedInputFileRepository {
    private final ManagedInputFileMyBatisMapper mapper;
    public MyBatisManagedInputFileRepository(ManagedInputFileMyBatisMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }
    @Override public long findOrRegister(String workspaceKey, long sysFileId, String path, String checksum, long sizeBytes) {
        if (workspaceKey == null || workspaceKey.isBlank() || path == null || path.isBlank() || checksum == null || checksum.isBlank() || sizeBytes < 0)
            throw new IllegalArgumentException("Managed input registration is invalid.");
        var existing = mapper.selectByWorkspaceAndSysFile(workspaceKey, sysFileId);
        if (existing == null) {
            mapper.insertManaged(workspaceKey, sysFileId, path, checksum, sizeBytes);
            existing = mapper.selectByWorkspaceAndSysFile(workspaceKey, sysFileId);
        }
        if (existing == null) throw new IllegalStateException("Managed input file could not be registered.");
        if (!path.equals(existing.getFilePathSnapshot()) || !checksum.equals(existing.getChecksum()) || sizeBytes != (existing.getSizeBytes() == null ? 0 : existing.getSizeBytes()))
            throw new IllegalArgumentException("Managed input file path or checksum does not match the existing registration.");
        mapper.touch(existing.getManagedFileId());
        return existing.getManagedFileId();
    }

    @Override public Optional<ManagedInputFile> findById(long managedFileId) {
        var entity = mapper.selectById(managedFileId);
        if (entity == null) return Optional.empty();
        return Optional.of(new ManagedInputFile(managedFileId, entity.getWorkspaceKey(), entity.getSysFileId(),
            Path.of(entity.getFilePathSnapshot()), entity.getChecksum(), entity.getSizeBytes() == null ? 0 : entity.getSizeBytes(),
            entity.getStatus(), entity.getVersion() == null ? 0 : entity.getVersion()));
    }
    @Override public Optional<ManagedInputFile> findByPath(Path path) {
        if (path == null) return Optional.empty();
        var entity = mapper.selectByPath(path.toAbsolutePath().normalize().toString());
        if (entity == null) return Optional.empty();
        return Optional.of(new ManagedInputFile(entity.getManagedFileId(), entity.getWorkspaceKey(), entity.getSysFileId(),
            Path.of(entity.getFilePathSnapshot()), entity.getChecksum(), entity.getSizeBytes() == null ? 0 : entity.getSizeBytes(),
            entity.getStatus(), entity.getVersion() == null ? 0 : entity.getVersion()));
    }
    @Override public void markPurged(long managedFileId, long expectedVersion) {
        if (mapper.markPurged(managedFileId, expectedVersion) == 0) throw new IllegalStateException("Managed input changed concurrently.");
    }
    @Override public void markPurgeFailed(long managedFileId, long expectedVersion, String reason) {
        if (mapper.markPurgeFailed(managedFileId, expectedVersion, reason) == 0) throw new IllegalStateException("Managed input changed concurrently.");
    }
}
