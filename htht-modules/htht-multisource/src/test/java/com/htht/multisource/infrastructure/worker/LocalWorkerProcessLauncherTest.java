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
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void returnsWorkerResponseWhenProcessExitsWithProtocolError() {
        LocalWorkerProcessLauncher launcher = launcher("invalid-manifest", Duration.ofSeconds(2));

        WorkerInvocationResult result = launcher.launch(new WorkerInvocation(
            "req-invalid-manifest", WorkerOperation.INSPECT, "{\"schemaVersion\":\"1.0\",\"requestId\":\"req-invalid-manifest\"}"
        ));

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.responseJson()).contains("INVALID_ASSET_MANIFEST");
    }

    @Test
    void terminatesTimedOutProcessAndRetainsDiagnosticDirectory() {
        LocalWorkerProcessLauncher launcher = launcher("sleep", Duration.ofMillis(100));

        assertThatThrownBy(() -> launcher.launch(new WorkerInvocation("req-timeout", WorkerOperation.INSPECT, "{}")))
            .isInstanceOf(WorkerProtocolException.class)
            .hasMessageContaining("timed out");

        assertThat(workRoot.resolve("req-timeout").resolve("request.json")).isRegularFile();
    }

    @Test
    void reportsExecuteStageTimeoutWithAStableRetryableCode() {
        LocalWorkerProcessLauncher launcher = launcher(
            "sleep", Duration.ofSeconds(2), Duration.ofMillis(100)
        );

        WorkerProtocolException failure = assertThrows(
            WorkerProtocolException.class,
            () -> launcher.launch(new WorkerInvocation("req-stage-timeout", WorkerOperation.EXECUTE_STAGE, "{}"))
        );

        assertThat(failure.code()).isEqualTo(WorkerFailurePresentation.WORKER_STAGE_TIMEOUT);
        assertThat(failure.workerMessage()).isEqualTo("execute-stage process exceeded configured timeout");
        assertThat(failure.retryable()).isTrue();
        assertThat(workRoot.resolve("req-stage-timeout").resolve("request.json")).isRegularFile();
    }

    @Test
    void canceledReservationDoesNotStartAWorkerProcess() {
        LocalWorkerProcessLauncher launcher = launcher("sleep", Duration.ofSeconds(2));
        launcher.reserve("req-canceled-before-start");
        launcher.cancel("req-canceled-before-start");

        assertThatThrownBy(() -> launcher.launch(new WorkerInvocation(
            "req-canceled-before-start", WorkerOperation.EXECUTE_STAGE, "{}"
        ))).isInstanceOf(CancellationException.class);

        assertThat(workRoot.resolve("req-canceled-before-start")).doesNotExist();
        launcher.release("req-canceled-before-start");
    }

    @Test
    void interruptedWaitTerminatesWorkerProcess() throws Exception {
        LocalWorkerProcessLauncher launcher = launcher("sleep", Duration.ofSeconds(30));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread launchThread = new Thread(() -> {
            try {
                launcher.launch(new WorkerInvocation("req-interrupted", WorkerOperation.EXECUTE_STAGE, "{}"));
            } catch (Throwable error) {
                failure.set(error);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        });

        launchThread.start();
        Path pidFile = workRoot.resolve("req-interrupted").resolve("worker.pid");
        for (int attempt = 0; attempt < 100 && !Files.isRegularFile(pidFile); attempt++) {
            Thread.sleep(50L);
        }
        assertThat(pidFile).isRegularFile();
        long workerPid = Long.parseLong(Files.readString(pidFile));

        launchThread.interrupt();
        launchThread.join(5_000L);

        assertThat(launchThread.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(WorkerProtocolException.class)
            .hasMessageContaining("process wait was interrupted");
        assertThat(interruptRestored.get()).isTrue();
        assertThat(ProcessHandle.of(workerPid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    @Test
    void usesDedicatedStageTimeoutForExecuteStage() {
        LocalProcessWorkerProperties properties = new LocalProcessWorkerProperties(
            List.of("python"), workRoot, Duration.ofMinutes(10), Duration.ofHours(12)
        );

        assertThat(properties.timeoutFor(WorkerOperation.INSPECT)).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.timeoutFor(WorkerOperation.BUILD_PLAN)).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.timeoutFor(WorkerOperation.EXECUTE_STAGE)).isEqualTo(Duration.ofHours(12));
    }

    private LocalWorkerProcessLauncher launcher(String mode, Duration timeout) {
        return launcher(mode, timeout, timeout);
    }

    private LocalWorkerProcessLauncher launcher(String mode, Duration timeout, Duration stageTimeout) {
        return new LocalWorkerProcessLauncher(new LocalProcessWorkerProperties(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), FixtureWorker.class.getName(), mode
            ),
            workRoot,
            timeout,
            stageTimeout
        ));
    }
}
