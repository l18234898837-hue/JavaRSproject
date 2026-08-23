package com.htht.multisource.infrastructure.worker;

import com.htht.multisource.application.port.out.model.ArtifactCandidate;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalWorkerArtifactValidatorTest {

    private static final TaskId TASK_ID = TaskId.of(7L);
    private static final StageId STAGE_ID = new StageId(201L);
    private static final ExecutionId EXECUTION_ID = new ExecutionId("exec-01");
    private static final StorageUri OUTPUT_URI = new StorageUri("rs://output/7/task-01");

    @TempDir
    Path tempRoot;

    @Test
    void acceptsRegularFileUnderExecutionOutputWithMatchingSizeAndChecksum() throws Exception {
        Path output = Files.createDirectories(tempRoot.resolve("exec-01/output"));
        byte[] content = "result".getBytes();
        Files.write(output.resolve("result.tif"), content);

        assertThatCode(() -> validator().validate(TASK_ID, STAGE_ID, EXECUTION_ID, OUTPUT_URI,
            candidate("result.tif", content))).doesNotThrowAnyException();
    }

    @Test
    void rejectsArtifactOutsideLogicalOutputOrExecutionDirectory() throws Exception {
        Path output = Files.createDirectories(tempRoot.resolve("exec-01/output"));
        byte[] content = "result".getBytes();
        Files.write(output.resolve("result.tif"), content);

        assertThatThrownBy(() -> validator().validate(TASK_ID, STAGE_ID, EXECUTION_ID, OUTPUT_URI,
            candidate("../result.tif", content))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator().validate(TASK_ID, STAGE_ID, EXECUTION_ID, OUTPUT_URI,
            new ArtifactCandidate(new StorageUri("rs://output/7/other/result.tif"), sha256(content), "OUTPUT", content.length)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingDirectoryFileAndMismatchedMetadata() throws Exception {
        Path output = Files.createDirectories(tempRoot.resolve("exec-01/output"));
        byte[] content = "result".getBytes();
        Files.write(output.resolve("result.tif"), content);
        Files.createDirectory(output.resolve("folder"));

        assertThatThrownBy(() -> validator().validate(TASK_ID, STAGE_ID, EXECUTION_ID, OUTPUT_URI,
            candidate("missing.tif", content))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator().validate(TASK_ID, STAGE_ID, EXECUTION_ID, OUTPUT_URI,
            new ArtifactCandidate(new StorageUri(OUTPUT_URI.value() + "/folder"), sha256(content), "OUTPUT", 0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator().validate(TASK_ID, STAGE_ID, EXECUTION_ID, OUTPUT_URI,
            new ArtifactCandidate(new StorageUri(OUTPUT_URI.value() + "/result.tif"), sha256(content), "OUTPUT", 99)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator().validate(TASK_ID, STAGE_ID, EXECUTION_ID, OUTPUT_URI,
            new ArtifactCandidate(new StorageUri(OUTPUT_URI.value() + "/result.tif"), "sha256:bad", "OUTPUT", content.length)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private LocalWorkerArtifactValidator validator() {
        return new LocalWorkerArtifactValidator(tempRoot);
    }

    private ArtifactCandidate candidate(String relativeName, byte[] content) throws Exception {
        return new ArtifactCandidate(new StorageUri(OUTPUT_URI.value() + "/" + relativeName),
            sha256(content), "OUTPUT", content.length);
    }

    private static String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder value = new StringBuilder("sha256:");
        for (byte item : digest) {
            value.append(String.format("%02x", item));
        }
        return value.toString();
    }
}
