package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.infrastructure.persistence.mapper.ManagedInputFileMyBatisMapper;
import java.util.Objects;

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
}
