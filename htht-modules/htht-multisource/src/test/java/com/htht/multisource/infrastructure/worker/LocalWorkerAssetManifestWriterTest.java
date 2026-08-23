package com.htht.multisource.infrastructure.worker;

import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalWorkerAssetManifestWriterTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @TempDir
    Path tempRoot;

    @Test
    void writesManifestOnlyForAuthorizedFilesAndCreatesInvocationOutput() throws Exception {
        Path assetRoot = Files.createDirectory(tempRoot.resolve("assets"));
        Path image = Files.writeString(assetRoot.resolve("image.jpg"), "image");
        LocalWorkerAssetManifestWriter writer = writer(assetRoot, Map.of("rs://input/image.jpg", image));

        Path manifestFile = writer.write("inspect-01", grant(), List.of(asset("BROWSE_IMAGE", 0, "rs://input/image.jpg")));

        assertThat(manifestFile).isEqualTo(tempRoot.resolve("work/inspect-01/asset-manifest.json"));
        assertThat(manifestFile).isRegularFile();
        assertThat(tempRoot.resolve("work/inspect-01/output")).isDirectory();
        JsonNode manifest = new ObjectMapper().readTree(Files.readString(manifestFile));
        assertThat(manifest.path("inputs").get(0).path("storageUri").asText()).isEqualTo("rs://input/image.jpg");
        assertThat(manifest.path("inputs").get(0).path("path").asText()).isEqualTo(image.toRealPath().toString());
    }

    @Test
    void rejectsResolvedPathOutsideConfiguredAssetRoot() throws Exception {
        Path assetRoot = Files.createDirectory(tempRoot.resolve("assets"));
        Path outside = Files.writeString(tempRoot.resolve("escape.jpg"), "escape");
        LocalWorkerAssetManifestWriter writer = writer(assetRoot, Map.of("rs://input/escape.jpg", outside));

        assertThatThrownBy(() -> writer.write("inspect-01", grant(), List.of(asset("BROWSE_IMAGE", 0, "rs://input/escape.jpg"))))
            .isInstanceOf(WorkerProtocolException.class).hasMessageContaining("asset root");
    }

    @Test
    void rejectsMissingFileDuplicateUriDuplicateRoleAndExpiredGrant() throws Exception {
        Path assetRoot = Files.createDirectory(tempRoot.resolve("assets"));
        Path image = Files.writeString(assetRoot.resolve("image.jpg"), "image");
        LocalWorkerAssetManifestWriter writer = writer(assetRoot, Map.of("rs://input/image.jpg", image));

        assertThatThrownBy(() -> writer.write("inspect-01", grant(), List.of(asset("BROWSE_IMAGE", 0, "rs://input/missing.jpg"))))
            .isInstanceOf(WorkerProtocolException.class).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> writer.write("inspect-01", grant(), List.of(
            asset("BROWSE_IMAGE", 0, "rs://input/image.jpg"), asset("BROWSE_IMAGE", 1, "rs://input/image.jpg"))))
            .isInstanceOf(WorkerProtocolException.class).hasMessageContaining("duplicate storageUri");
        assertThatThrownBy(() -> writer.write("inspect-01", grant(), List.of(
            asset("BROWSE_IMAGE", 0, "rs://input/image.jpg"), asset("BROWSE_IMAGE", 0, "rs://input/other.jpg"))))
            .isInstanceOf(WorkerProtocolException.class).hasMessageContaining("role and ordinal");
        assertThatThrownBy(() -> writer.write("inspect-01", expiredGrant(), List.of(asset("BROWSE_IMAGE", 0, "rs://input/image.jpg"))))
            .isInstanceOf(WorkerProtocolException.class).hasMessageContaining("expired");
    }

    @Test
    void stageManifestUsesControlledOutputDirectoryForLogicalOutputUri() throws Exception {
        Path assetRoot = Files.createDirectory(tempRoot.resolve("assets"));
        Path input = Files.writeString(assetRoot.resolve("input.h5"), "input");
        LocalWorkerAssetManifestWriter writer = writer(assetRoot, Map.of("rs://input/input.h5", input));

        Path manifestFile = writer.write("stage-01", executionGrant(),
            List.of(asset("MAIN", 0, "rs://input/input.h5")), new StorageUri("rs://output/../escape.tif"));

        assertThat(manifestFile).isRegularFile();
        JsonNode manifest = new ObjectMapper().readTree(Files.readString(manifestFile));
        assertThat(manifest.path("output").path("path").asText())
            .isEqualTo(tempRoot.resolve("work/exec-01/output").toString());
        assertThat(manifest.toString()).doesNotContain("escape.tif");
    }

    private LocalWorkerAssetManifestWriter writer(Path assetRoot, Map<String, Path> paths) {
        return new LocalWorkerAssetManifestWriter(
            tempRoot.resolve("work"), assetRoot, (grantReference, storageUri) -> paths.get(storageUri.value()),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static AssetAccessGrant grant() {
        return new AssetAccessGrant("read-01", NOW.plusSeconds(60));
    }

    private static AssetAccessGrant expiredGrant() {
        return new AssetAccessGrant("read-expired", NOW.minusSeconds(1));
    }

    private static ExecutionGrant executionGrant() {
        return new ExecutionGrant("write-01", NOW.plusSeconds(60), TaskId.of(101L), new StageId(201L),
            new com.htht.multisource.domain.model.ExecutionId("exec-01"), 1);
    }

    private static InputAsset asset(String role, int ordinal, String uri) {
        return new InputAsset(role, ordinal, new AssetReference("LOCAL_FILE", uri.substring(uri.lastIndexOf('/') + 1)),
            new StorageUri(uri), "sha256:test", true);
    }
}
