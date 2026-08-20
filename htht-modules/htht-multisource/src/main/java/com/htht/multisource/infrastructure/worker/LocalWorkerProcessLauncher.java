package com.htht.multisource.infrastructure.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class LocalWorkerProcessLauncher implements WorkerProcessLauncher {

    private final LocalProcessWorkerProperties properties;
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

    public LocalWorkerProcessLauncher(LocalProcessWorkerProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public WorkerInvocationResult launch(WorkerInvocation invocation) {
        Path invocationDirectory = properties.workRoot().resolve(invocation.requestId()).normalize();
        if (!invocationDirectory.startsWith(properties.workRoot())) {
            throw protocolFailure(invocation, "request directory escapes configured work root", null);
        }
        Path requestFile = invocationDirectory.resolve("request.json");
        Path responseFile = invocationDirectory.resolve("response.json");
        Path stdoutFile = invocationDirectory.resolve("stdout.log");
        Path stderrFile = invocationDirectory.resolve("stderr.log");
        try {
            Files.createDirectories(properties.workRoot());
            Files.createDirectory(invocationDirectory);
            Files.writeString(requestFile, invocation.requestJson(), StandardCharsets.UTF_8);
            Process process = startProcess(requestFile, responseFile, stdoutFile, stderrFile);
            runningProcesses.put(invocation.requestId(), process);
            try {
                if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    terminate(process);
                    throw protocolFailure(invocation, "process timed out", null);
                }
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw protocolFailure(invocation, "process exited with code " + exitCode, null);
                }
                if (!Files.isRegularFile(responseFile)) {
                    throw protocolFailure(invocation, "response file was not written", null);
                }
                return new WorkerInvocationResult(
                    invocation.requestId(), exitCode, Files.readString(responseFile, StandardCharsets.UTF_8)
                );
            } finally {
                runningProcesses.remove(invocation.requestId(), process);
            }
        } catch (IOException exception) {
            throw protocolFailure(invocation, "process files could not be created or read", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw protocolFailure(invocation, "process wait was interrupted", exception);
        }
    }

    @Override
    public void cancel(String requestId) {
        Process process = runningProcesses.remove(requestId);
        if (process != null) {
            terminate(process);
        }
    }

    private Process startProcess(Path requestFile, Path responseFile, Path stdoutFile, Path stderrFile)
        throws IOException {
        ArrayList<String> command = new ArrayList<>(properties.command());
        command.add("--request");
        command.add(requestFile.toString());
        command.add("--response");
        command.add(responseFile.toString());
        return new ProcessBuilder(command)
            .redirectOutput(stdoutFile.toFile())
            .redirectError(stderrFile.toFile())
            .start();
    }

    private static void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static WorkerProtocolException protocolFailure(
        WorkerInvocation invocation, String message, Throwable cause
    ) {
        return cause == null
            ? new WorkerProtocolException(invocation.requestId(), invocation.operation(), message)
            : new WorkerProtocolException(invocation.requestId(), invocation.operation(), message, cause);
    }
}
