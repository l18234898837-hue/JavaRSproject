package com.htht.multisource.infrastructure.worker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalWorkerProcessLauncherTest {

    @TempDir
    Path workRoot;

    @AfterEach
    void removeTestWorkDirectories() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                deleteTestWorkDirectories();
                return;
            } catch (FileSystemException exception) {
                if (attempt == 19) {
                    throw exception;
                }
                Thread.sleep(50L);
            }
        }
    }

    private void deleteTestWorkDirectories() throws Exception {
        List<Path> paths;
        try (var walk = Files.walk(workRoot)) {
            paths = walk.sorted(Comparator.reverseOrder())
                .filter(path -> !path.equals(workRoot))
                .toList();
        }
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    @Test
    void writesRequestStartsConfiguredCommandAndReturnsResponseFile() {
        LocalWorkerProcessLauncher launcher = launcher("success", Duration.ofSeconds(2));

        WorkerInvocationResult result = launcher.launch(new WorkerInvocation(
            "req-01", WorkerOperation.INSPECT, "{\"schemaVersion\":\"1.0\",\"requestId\":\"req-01\"}"
        ));

        assertThat(result.requestId()).isEqualTo("req-01");
        assertThat(result.responseJson()).contains("\"requestId\":\"req-01\"");
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void passesAssetManifestToWorkerAlongsideRequestAndResponse() throws Exception {
        LocalWorkerProcessLauncher launcher = launcher("success", Duration.ofSeconds(2));
        Path invocationDirectory = workRoot.resolve("req-manifest");
        Files.createDirectories(invocationDirectory.resolve("output"));
        Path manifest = Files.writeString(invocationDirectory.resolve("asset-manifest.json"), "{}");

        launcher.launch(new WorkerInvocation(
            "req-manifest", WorkerOperation.INSPECT, "{\"schemaVersion\":\"1.0\",\"requestId\":\"req-manifest\"}", manifest
        ));

        assertThat(Files.readString(invocationDirectory.resolve("manifest-seen.txt"))).isEqualTo(manifest.toString());
    }

    @Test
    void failsWhenSuccessfulProcessDoesNotWriteResponseFile() {
        LocalWorkerProcessLauncher launcher = launcher("missing", Duration.ofSeconds(2));

        assertThatThrownBy(() -> launcher.launch(new WorkerInvocation("req-02", WorkerOperation.INSPECT, "{}")))
            .isInstanceOf(WorkerProtocolException.class)
            .hasMessageContaining("response");
    }

    @Test
    void terminatesTimedOutProcessAndRetainsDiagnosticDirectory() {
        LocalWorkerProcessLauncher launcher = launcher("sleep", Duration.ofMillis(100));

        assertThatThrownBy(() -> launcher.launch(new WorkerInvocation("req-timeout", WorkerOperation.INSPECT, "{}")))
            .isInstanceOf(WorkerProtocolException.class)
            .hasMessageContaining("timed out");

        assertThat(workRoot.resolve("req-timeout").resolve("request.json")).isRegularFile();
    }

    private LocalWorkerProcessLauncher launcher(String mode, Duration timeout) {
        return new LocalWorkerProcessLauncher(new LocalProcessWorkerProperties(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), FixtureWorker.class.getName(), mode
            ),
            workRoot,
            timeout
        ));
    }
}
