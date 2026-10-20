package com.htht.config.multisource;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.AssetMetadata;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrantRequest;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.worker.LocalWorkerAssetResolver;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Bridges sys_file records to controlled Worker assets. sys_file has no owner field, so this adapter
 * can validate existence and path safety but cannot establish per-file ownership.
 */
public final class SysFileAssetStorageAdapter implements AssetStorage, LocalWorkerAssetResolver {
    /** 限制进程内校验缓存，避免大量历史文件导致长期运行的服务持续占用内存。 */
    private static final int MAX_CHECKSUM_CACHE_ENTRIES = 2_048;

    private final ISysFileService sysFileService;
    private final Path uploadRoot;
    private final Map<String, GrantScope> grants = new ConcurrentHashMap<>();
    private final Map<Path, CachedChecksum> checksumCache = new ConcurrentHashMap<>();

    public SysFileAssetStorageAdapter(ISysFileService sysFileService, Path uploadRoot) {
        this.sysFileService = Objects.requireNonNull(sysFileService, "sysFileService");
        this.uploadRoot = realDirectory(uploadRoot);
    }

    public InputAsset toInputAsset(String role, int ordinal, AssetReference reference, boolean required) {
        Path path = resolveReference(reference);
        String id = reference.sourceId();
        return new InputAsset(role, ordinal, reference, new StorageUri("rs://sys-file/" + id), checksum(path), required);
    }

    @Override
    public AssetStorage.ManagedInputFileMetadata managedInputFile(AccessSubject subject, AssetReference reference) {
        Path path = resolveReference(reference);
        try {
            return new AssetStorage.ManagedInputFileMetadata(path.toString(), Files.size(path), checksum(path));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to inspect sys file asset.", exception);
        }
    }

    @Override
    public Path resolve(String grantReference, StorageUri storageUri) {
        return resolveSysFile(authorizedSysFile(grantReference, storageUri));
    }

    @Override
    public String originalFileName(String grantReference, StorageUri storageUri, Path resolvedPath) {
        SysFile file = authorizedSysFile(grantReference, storageUri);
        Path authorizedPath = resolveSysFile(file);
        try {
            if (resolvedPath == null || !authorizedPath.equals(resolvedPath.toRealPath())) {
                throw new IllegalArgumentException("Resolved sys file does not match the authorized asset.");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Resolved sys file cannot be read.", exception);
        }
        String baseName = file.getFileName();
        String extension = file.getExtension();
        return extension == null || extension.isBlank() ? baseName : baseName + "." + extension;
    }

    public AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs) {
        String reference = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(300);
        grants.put(reference, new GrantScope(expiresAt, storageUris(inputs)));
        return new AssetAccessGrant(reference, expiresAt);
    }

    public ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri) {
        throw new UnsupportedOperationException("Execution grants must bind a stage execution.");
    }

    public ExecutionGrant grant(ExecutionGrantRequest request) {
        String reference = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(300);
        grants.put(reference, new GrantScope(expiresAt, storageUris(request.inputs())));
        return new ExecutionGrant(reference, expiresAt, request.taskId(), request.stageId(), request.executionId(), request.attempt());
    }

    void authorize(String grantReference, Instant expiresAt, Set<String> storageUris) {
        grants.put(grantReference, new GrantScope(expiresAt, storageUris));
    }

    public AssetMetadata resolve(AccessSubject subject, AssetReference reference) {
        Path path = resolveReference(reference);
        try {
            return new AssetMetadata(new StorageUri("rs://sys-file/" + reference.sourceId()), Files.size(path), checksum(path));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to inspect sys file asset.", exception);
        }
    }

    private Path resolveReference(AssetReference reference) {
        if (reference == null || !"SYS_FILE".equals(reference.sourceType()) || !reference.sourceId().matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("Only positive SYS_FILE references are supported.");
        }
        return resolveSysFile(requireSysFile(reference.sourceId()));
    }

    private SysFile authorizedSysFile(String grantReference, StorageUri storageUri) {
        if (storageUri == null || !storageUri.value().matches("rs://sys-file/[1-9][0-9]*")) {
            throw new IllegalArgumentException("Unsupported sys file storage URI.");
        }
        GrantScope scope = grants.get(grantReference);
        if (scope == null || !scope.expiresAt.isAfter(Instant.now()) || !scope.storageUris.contains(storageUri.value())) {
            throw new IllegalArgumentException("Sys file is not authorized by the grant.");
        }
        return requireSysFile(storageUri.value().substring("rs://sys-file/".length()));
    }

    private SysFile requireSysFile(String id) {
        SysFile file = sysFileService.getById(Long.parseLong(id));
        if (file == null || file.getFilePath() == null || file.getFilePath().isBlank()) {
            throw new IllegalArgumentException("Sys file does not exist.");
        }
        return file;
    }

    private Path resolveSysFile(SysFile file) {
        try {
            Path path = Path.of(file.getFilePath()).toRealPath();
            if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalArgumentException("Sys file is outside the managed upload root.");
            }
            return path;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Sys file cannot be read.", exception);
        }
    }

    private static Path realDirectory(Path root) {
        try {
            return Objects.requireNonNull(root, "uploadRoot").toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Upload root cannot be resolved.", exception);
        }
    }

    /**
     * 复用未发生变化的文件校验值，避免预检和任务创建重复顺序读取数 GB 的遥感影像。
     * 文件大小或最后修改时间改变时会立即重新计算。
     */
    private String checksum(Path path) {
        FileSnapshot before = fileSnapshot(path);
        CachedChecksum cached = checksumCache.get(path);
        if (cached != null && cached.snapshot().equals(before)) {
            return cached.value();
        }
        try (InputStream stream = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            for (int read; (read = stream.read(buffer)) >= 0;) {
                digest.update(buffer, 0, read);
            }
            String value = "sha256:" + HexFormat.of().formatHex(digest.digest());
            FileSnapshot after = fileSnapshot(path);
            if (before.equals(after)) {
                if (checksumCache.size() >= MAX_CHECKSUM_CACHE_ENTRIES) {
                    checksumCache.clear();
                }
                checksumCache.put(path, new CachedChecksum(after, value));
            }
            return value;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException("Sys file checksum cannot be calculated.", exception);
        }
    }

    private static FileSnapshot fileSnapshot(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new FileSnapshot(attributes.size(), attributes.lastModifiedTime().toMillis());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Sys file metadata cannot be read.", exception);
        }
    }

    private static Set<String> storageUris(List<InputAsset> inputs) {
        return inputs.stream().map(asset -> asset.storageUriSnapshot().value()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private record GrantScope(Instant expiresAt, Set<String> storageUris) {
    }

    private record FileSnapshot(long size, long lastModifiedMillis) {
    }

    private record CachedChecksum(FileSnapshot snapshot, String value) {
    }
}
