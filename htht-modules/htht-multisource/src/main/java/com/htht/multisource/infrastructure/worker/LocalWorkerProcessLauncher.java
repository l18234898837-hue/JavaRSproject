package com.htht.multisource.infrastructure.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class LocalWorkerProcessLauncher implements WorkerProcessLauncher {

    private final LocalProcessWorkerProperties properties;
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<String, RequestState> scheduledRequests = new HashMap<>();
    private final Object processLifecycleMonitor = new Object();

    public LocalWorkerProcessLauncher(LocalProcessWorkerProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void reserve(String requestId) {
        Objects.requireNonNull(requestId, "requestId");
        synchronized (processLifecycleMonitor) {
            if (scheduledRequests.containsKey(requestId) || runningProcesses.containsKey(requestId)) {
                throw new IllegalStateException("Worker request is already reserved: " + requestId);
            }
            scheduledRequests.put(requestId, RequestState.READY);
        }
    }

    @Override
    public WorkerInvocationResult launch(WorkerInvocation invocation) {
        boolean implicitReservation = ensureReserved(invocation.requestId());
        Path invocationDirectory = properties.workRoot().resolve(invocation.requestId()).normalize();
        try {
            if (!invocationDirectory.startsWith(properties.workRoot())) {
                throw protocolFailure(invocation, "request directory escapes configured work root", null);
            }
            Path requestFile = invocationDirectory.resolve("request.json");
            Path responseFile = invocationDirectory.resolve("response.json");
            Path manifestFile = invocation.manifestFile();
            if (manifestFile != null && (!manifestFile.normalize().startsWith(properties.workRoot())
                || !Files.isRegularFile(manifestFile))) {
                throw protocolFailure(invocation, "asset manifest is outside work root or missing", null);
            }
            Path outputDirectory = invocationDirectory.resolve("output");
            Path stdoutFile = invocationDirectory.resolve("stdout.log");
            Path stderrFile = invocationDirectory.resolve("stderr.log");
            try {
                Files.createDirectories(properties.workRoot());
                Files.createDirectories(invocationDirectory);
                Files.createDirectories(outputDirectory);
                Files.deleteIfExists(responseFile);
                Files.writeString(requestFile, invocation.requestJson(), StandardCharsets.UTF_8);
                Process process = startRegisteredProcess(
                    invocation.requestId(), requestFile, manifestFile, responseFile, stdoutFile, stderrFile
                );
                try {
                    long timeoutMillis = properties.timeoutFor(invocation.operation()).toMillis();
                    if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                        terminate(process);
                        if (invocation.operation() == WorkerOperation.EXECUTE_STAGE) {
                            throw new WorkerProtocolException(
                                invocation.requestId(), invocation.operation(),
                                WorkerFailurePresentation.WORKER_STAGE_TIMEOUT,
                                "execute-stage process exceeded configured timeout", true
                            );
                        }
                        throw protocolFailure(invocation, "process timed out", null);
                    }
                    throwIfCanceled(invocation.requestId());
                    int exitCode = process.exitValue();
                    if (!Files.isRegularFile(responseFile)) {
                        throw protocolFailure(invocation, "response file was not written (exit code " + exitCode + ")", null);
                    }
                    return new WorkerInvocationResult(
                        invocation.requestId(), exitCode, Files.readString(responseFile, StandardCharsets.UTF_8)
                    );
                } catch (InterruptedException exception) {
                    terminate(process);
                    Thread.currentThread().interrupt();
                    throw protocolFailure(invocation, "process wait was interrupted", exception);
                } finally {
                    runningProcesses.remove(invocation.requestId(), process);
                }
            } catch (IOException exception) {
                throw protocolFailure(invocation, "process files could not be created or read", exception);
            }
        } finally {
            if (implicitReservation) {
                release(invocation.requestId());
            }
        }
    }

    @Override
    public void cancel(String requestId) {
        Process process;
        synchronized (processLifecycleMonitor) {
            if (!scheduledRequests.containsKey(requestId) && !runningProcesses.containsKey(requestId)) {
                return;
            }
            scheduledRequests.put(requestId, RequestState.CANCELED);
            process = runningProcesses.remove(requestId);
        }
        if (process != null) {
            terminate(process);
        }
    }

    @Override
    public void release(String requestId) {
        synchronized (processLifecycleMonitor) {
            scheduledRequests.remove(requestId);
        }
    }

    private boolean ensureReserved(String requestId) {
        synchronized (processLifecycleMonitor) {
            RequestState state = scheduledRequests.get(requestId);
            if (state == RequestState.CANCELED) {
                throw canceled(requestId);
            }
            if (state == null) {
                scheduledRequests.put(requestId, RequestState.READY);
                return true;
            }
            return false;
        }
    }

    private Process startRegisteredProcess(
        String requestId, Path requestFile, Path manifestFile, Path responseFile, Path stdoutFile, Path stderrFile
    ) throws IOException {
        synchronized (processLifecycleMonitor) {
            // A release may race with the file-preparation portion of launch(). Do not
            // start a process unless this invocation still owns a READY reservation.
            if (scheduledRequests.get(requestId) != RequestState.READY) {
                throw canceled(requestId);
            }
            Process process = startProcess(requestFile, manifestFile, responseFile, stdoutFile, stderrFile);
            runningProcesses.put(requestId, process);
            return process;
        }
    }

    private void throwIfCanceled(String requestId) {
        synchronized (processLifecycleMonitor) {
            if (scheduledRequests.get(requestId) == RequestState.CANCELED) {
                throw canceled(requestId);
            }
        }
    }

    private static CancellationException canceled(String requestId) {
        return new CancellationException("Worker request was canceled before completion: " + requestId);
    }

    private Process startProcess(Path requestFile, Path manifestFile, Path responseFile, Path stdoutFile, Path stderrFile)
        throws IOException {
        ArrayList<String> command = new ArrayList<>(properties.command());
        command.add("--request");
        command.add(requestFile.toString());
        if (manifestFile != null) {
            command.add("--asset-manifest");
            command.add(manifestFile.toString());
        }
        command.add("--response");
        command.add(responseFile.toString());
        return new ProcessBuilder(command)
            .redirectOutput(stdoutFile.toFile())
            .redirectError(stderrFile.toFile())
            .start();
    }

    private static void terminate(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        boolean interrupted = false;
        try {
            if (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
            forceAlive(descendants);
            waitUntilExited(process, descendants, 5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            interrupted = true;
            forceAlive(descendants);
            process.destroyForcibly();
        } finally {
            forceAlive(descendants);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void forceAlive(List<ProcessHandle> processes) {
        processes.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }

    private static void waitUntilExited(
        Process process, List<ProcessHandle> descendants, long timeout, TimeUnit unit
    ) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return;
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(25)));
        }
    }

    private enum RequestState {
        READY,
        CANCELED
    }

    private static WorkerProtocolException protocolFailure(
        WorkerInvocation invocation, String message, Throwable cause
    ) {
        return cause == null
            ? new WorkerProtocolException(invocation.requestId(), invocation.operation(), message)
            : new WorkerProtocolException(invocation.requestId(), invocation.operation(), message, cause);
    }
}
