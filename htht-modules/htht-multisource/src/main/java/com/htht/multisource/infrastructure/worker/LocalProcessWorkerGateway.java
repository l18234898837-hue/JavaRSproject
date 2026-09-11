package com.htht.multisource.infrastructure.worker;

import com.htht.multisource.application.port.out.ProcessingPlanner;
import com.htht.multisource.application.port.out.ProductInspector;
import com.htht.multisource.application.port.out.TaskDispatcher;
import com.htht.multisource.application.port.out.model.DispatchReceipt;
import com.htht.multisource.application.port.out.model.ArtifactInspectionRequest;
import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.InspectionResponse;
import com.htht.multisource.application.port.out.model.PlanRequest;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.ProcessingPlan;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

public final class LocalProcessWorkerGateway implements ProductInspector, ProcessingPlanner, TaskDispatcher {

    private final WorkerContractCodec codec;
    private final WorkerResultTranslator translator;
    private final WorkerProcessLauncher launcher;
    private final LocalWorkerAssetManifestWriter manifestWriter;
    private final Supplier<String> requestIdSupplier;
    private final Map<ExecutionId, ReservedRequest> executionRequests = new ConcurrentHashMap<>();
    private final Object executionMonitor = new Object();

    public LocalProcessWorkerGateway(
        WorkerContractCodec codec,
        WorkerResultTranslator translator,
        WorkerProcessLauncher launcher,
        Supplier<String> requestIdSupplier
    ) {
        this(codec, translator, launcher, requestIdSupplier, null);
    }

    public LocalProcessWorkerGateway(
        WorkerContractCodec codec,
        WorkerResultTranslator translator,
        WorkerProcessLauncher launcher,
        Supplier<String> requestIdSupplier,
        LocalWorkerAssetManifestWriter manifestWriter
    ) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.translator = Objects.requireNonNull(translator, "translator");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier");
        this.manifestWriter = manifestWriter;
    }

    @Override
    public InspectionResponse inspect(InspectionRequest request) {
        String requestId = nextRequestId(WorkerOperation.INSPECT);
        return translator.toInspection(invoke(requestId, WorkerOperation.INSPECT, codec.encodeInspect(requestId, request),
            manifestWriter == null ? null : manifestWriter.write(requestId, request.readGrant(), request.inputs())));
    }

    public com.fasterxml.jackson.databind.JsonNode inspectArtifact(ArtifactInspectionRequest request) {
        Objects.requireNonNull(request, "request");
        String requestId = nextRequestId(WorkerOperation.INSPECT_ARTIFACT);
        return invoke(requestId, WorkerOperation.INSPECT_ARTIFACT, codec.encodeArtifactInspection(requestId, request),
            manifestWriter == null ? null : manifestWriter.write(requestId, request.readGrant(), java.util.List.of(request.artifact()))
        ).payload();
    }

    @Override
    public ProcessingPlan buildPlan(PlanRequest request) {
        String requestId = nextRequestId(WorkerOperation.BUILD_PLAN);
        return translator.toPlan(invoke(requestId, WorkerOperation.BUILD_PLAN, codec.encodeBuildPlan(requestId, request),
            manifestWriter == null ? null : manifestWriter.write(requestId, request.readGrant(), request.candidate().files())));
    }

    @Override
    public void reserve(ExecutionId executionId) {
        Objects.requireNonNull(executionId, "executionId");
        String requestId = nextRequestId(WorkerOperation.EXECUTE_STAGE);
        synchronized (executionMonitor) {
            if (executionRequests.containsKey(executionId)) {
                throw new IllegalStateException("Worker execution is already reserved: " + executionId.value());
            }
            launcher.reserve(requestId);
            executionRequests.put(executionId, new ReservedRequest(requestId, false));
        }
    }

    @Override
    public DispatchReceipt dispatch(StageExecutionRequest request) {
        DispatchRequest dispatchRequest = requestForDispatch(request.executionId());
        String requestId = dispatchRequest.requestId();
        try {
            return translator.toDispatchReceipt(invoke(
                requestId, WorkerOperation.EXECUTE_STAGE, codec.encodeStageExecution(requestId, request),
                manifestWriter == null ? null : manifestWriter.write(
                    requestId, request.executionGrant(), request.inputs(), request.outputUri())
            ));
        } finally {
            if (dispatchRequest.implicitReservation()) {
                release(request.executionId(), requestId);
            }
        }
    }

    @Override
    public void cancel(ExecutionId executionId) {
        String requestId = null;
        synchronized (executionMonitor) {
            ReservedRequest request = executionRequests.get(executionId);
            if (request != null) {
                executionRequests.put(executionId, request.canceledRequest());
                requestId = request.requestId();
            }
        }
        if (requestId != null) {
            launcher.cancel(requestId);
        }
    }

    @Override
    public void release(ExecutionId executionId) {
        synchronized (executionMonitor) {
            ReservedRequest request = executionRequests.remove(executionId);
            if (request != null) {
                launcher.release(request.requestId());
            }
        }
    }

    private DispatchRequest requestForDispatch(ExecutionId executionId) {
        synchronized (executionMonitor) {
            ReservedRequest request = executionRequests.get(executionId);
            if (request == null) {
                String requestId = nextRequestId(WorkerOperation.EXECUTE_STAGE);
                launcher.reserve(requestId);
                executionRequests.put(executionId, new ReservedRequest(requestId, false));
                return new DispatchRequest(requestId, true);
            }
            if (request.canceled()) {
                executionRequests.remove(executionId);
                launcher.release(request.requestId());
                throw new CancellationException("Worker execution was canceled before process start.");
            }
            return new DispatchRequest(request.requestId(), false);
        }
    }

    private void release(ExecutionId executionId, String requestId) {
        synchronized (executionMonitor) {
            ReservedRequest current = executionRequests.get(executionId);
            if (current != null && current.requestId().equals(requestId)) {
                executionRequests.remove(executionId);
                launcher.release(requestId);
            }
        }
    }

    private DecodedWorkerResponse invoke(String requestId, WorkerOperation operation, String requestJson) {
        return invoke(requestId, operation, requestJson, null);
    }

    private DecodedWorkerResponse invoke(String requestId, WorkerOperation operation, String requestJson,
                                         java.nio.file.Path manifestFile) {
        WorkerInvocationResult result = launcher.launch(new WorkerInvocation(requestId, operation, requestJson, manifestFile));
        return codec.decodeResponse(requestId, operation, result.responseJson());
    }

    private String nextRequestId(WorkerOperation operation) {
        String requestId = requestIdSupplier.get();
        if (requestId == null || requestId.isBlank()) {
            throw new WorkerProtocolException(String.valueOf(requestId), operation, "requestId supplier returned blank value");
        }
        return requestId;
    }

    private record ReservedRequest(String requestId, boolean canceled) {
        private ReservedRequest canceledRequest() {
            return canceled ? this : new ReservedRequest(requestId, true);
        }
    }

    private record DispatchRequest(String requestId, boolean implicitReservation) {
    }
}
