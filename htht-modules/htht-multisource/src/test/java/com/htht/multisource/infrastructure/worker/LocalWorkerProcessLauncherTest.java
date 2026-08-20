package com.htht.multisource.infrastructure.worker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalWorkerProcessLauncherTest {

    @TempDir
    Path workRoot;

    @AfterEach
    void removeTestWorkDirectories() throws Exception {
        try (var paths = Files.walk(workRoot)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                .filter(path -> !path.equals(workRoot))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
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
    void failsWhenSuccessfulProcessDoesNotWriteResponseFile() {
        LocalWorkerProcessLauncher launcher = launcher("missing", Duration.ofSeconds(2));

        assertThatThrownBy(() -> launcher.launch(new WorkerInvocation("req-02", WorkerOperation.INSPECT, "{}")))
            .isInstanceOf(WorkerProtocolException.class)
            .hasMessageContaining("response");
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
