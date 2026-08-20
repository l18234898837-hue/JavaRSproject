package com.htht.multisource.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StorageUri;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class LocalWorkerAssetManifestWriter {

    private final Path workRoot;
    private final Path assetRoot;
    private final LocalWorkerAssetResolver resolver;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public LocalWorkerAssetManifestWriter(
        Path workRoot,
        Path assetRoot,
        LocalWorkerAssetResolver resolver,
        Clock clock
    ) {
        this.workRoot = absoluteDirectory(workRoot, "workRoot");
        this.assetRoot = absoluteDirectory(assetRoot, "assetRoot");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = new ObjectMapper();
    }

    public Path write(String requestId, AssetAccessGrant grant, List<InputAsset> assets) {
        return writeInternal(requestId, grant.grantReference(), grant.expiresAt(), assets);
    }

    public Path write(String requestId, ExecutionGrant grant, List<InputAsset> assets, StorageUri outputUri) {
        Objects.requireNonNull(outputUri, "outputUri");
        return writeInternal(requestId, grant.grantReference(), grant.expiresAt(), assets);
    }

    public Path write(String requestId, ExecutionGrant grant, List<InputAsset> assets) {
        return writeInternal(requestId, grant.grantReference(), grant.expiresAt(), assets);
    }

    private Path writeInternal(String requestId, String grantReference, Instant expiresAt, List<InputAsset> assets) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw failure(requestId, "requestId is invalid");
        }
        Objects.requireNonNull(assets, "assets");
        Instant now = clock.instant();
        if (!expiresAt.isAfter(now)) {
            throw failure(requestId, "grant is expired");
        }

        Set<String> storageUris = new HashSet<>();
        Set<String> coordinates = new HashSet<>();
        List<WorkerAssetManifest.Input> inputs = assets.stream().map(asset -> {
            if (!storageUris.add(asset.storageUriSnapshot().value())) {
                throw failure(requestId, "duplicate storageUri");
            }
            if (!coordinates.add(asset.role() + "\u0000" + asset.ordinal())) {
                throw failure(requestId, "duplicate role and ordinal");
            }
            Path resolved = resolver.resolve(grantReference, asset.storageUriSnapshot());
            Path real = requireAssetFile(requestId, resolved);
            return new WorkerAssetManifest.Input(asset.storageUriSnapshot().value(), asset.role(), asset.ordinal(),
                real, asset.checksum());
        }).toList();

        Path invocationDirectory = workRoot.resolve(requestId).normalize();
        Path outputPath = invocationDirectory.resolve("output").normalize();
        if (!outputPath.startsWith(workRoot) || !outputPath.startsWith(invocationDirectory)) {
            throw failure(requestId, "output directory escapes work root");
        }
        try {
            Files.createDirectories(outputPath);
            WorkerAssetManifest manifest = new WorkerAssetManifest(requestId, grantReference, expiresAt, inputs, outputPath);
            ObjectNode root = objectMapper.createObjectNode();
            root.put("manifestVersion", "1.0");
            root.put("requestId", manifest.requestId());
            root.put("grantReference", manifest.grantReference());
            root.put("expiresAt", manifest.expiresAt().toString());
            ArrayNode inputNodes = root.putArray("inputs");
            manifest.inputs().forEach(input -> {
                ObjectNode node = inputNodes.addObject();
                node.put("storageUri", input.storageUri());
                node.put("role", input.role());
                node.put("ordinal", input.ordinal());
                node.put("path", input.path().toString());
                node.put("checksum", input.checksum());
            });
            root.putObject("output").put("path", manifest.outputPath().toString());
            Path manifestFile = invocationDirectory.resolve("asset-manifest.json");
            Path temporary = invocationDirectory.resolve("asset-manifest.json.tmp");
            Files.writeString(temporary, objectMapper.writeValueAsString(root), StandardCharsets.UTF_8);
            Files.move(temporary, manifestFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return manifestFile;
        } catch (IOException exception) {
            throw failure(requestId, "manifest could not be written", exception);
        }
    }

    private Path requireAssetFile(String requestId, Path resolved) {
        if (resolved == null || !Files.exists(resolved)) {
            throw failure(requestId, "asset does not exist");
        }
        try {
            Path real = resolved.toRealPath();
            Path root = assetRoot.toRealPath();
            if (!real.startsWith(root) || !Files.isRegularFile(real) || !Files.isReadable(real)) {
                throw failure(requestId, "asset path escapes configured asset root");
            }
            return real;
        } catch (IOException exception) {
            throw failure(requestId, "asset could not be resolved", exception);
        }
    }

    private static Path absoluteDirectory(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }

    private static WorkerProtocolException failure(String requestId, String message) {
        return new WorkerProtocolException(String.valueOf(requestId), WorkerOperation.INSPECT, message);
    }

    private static WorkerProtocolException failure(String requestId, String message, Throwable cause) {
        return new WorkerProtocolException(String.valueOf(requestId), WorkerOperation.INSPECT, message, cause);
    }
}
