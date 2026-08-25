package com.htht.config.multisource;

import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class LocalTaskArtifactStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void promotesWorkerOutputBeforeTheExecutionDirectoryIsRemoved() throws Exception {
        Path workRoot = Files.createDirectories(temporaryDirectory.resolve("worker"));
        Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
        Path workerOutput = Files.createDirectories(workRoot.resolve("exec-01/output"));
        Path source = Files.writeString(workerOutput.resolve("result.json"), "result");
        ArtifactCandidate candidate = new ArtifactCandidate(
            new StorageUri("rs://output/7/task-01/result.json"), checksum(source), "SUMMARY", Files.size(source));

        LocalTaskArtifactStore store = new LocalTaskArtifactStore(workRoot, artifactRoot);
        var artifact = store.materialize(TaskId.of(101L), new StageId(201L), "WRITE_STANDARD", 1,
            new ExecutionId("exec-01"), new StorageUri("rs://output/7/task-01"), candidate,
            new StageOutput("SUMMARY", "result.json", StageOutput.Retention.FINAL));

        Files.delete(source);

        assertThat(artifact.storageKey()).isEqualTo("101/output/result.json");
        assertThat(Files.readString(store.resolveForDownload(artifact))).isEqualTo("result");
    }

    private static String checksum(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        return "sha256:" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
