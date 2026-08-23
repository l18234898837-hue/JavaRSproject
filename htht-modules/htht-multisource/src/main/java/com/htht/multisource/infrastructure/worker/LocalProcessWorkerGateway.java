package com.htht.multisource.infrastructure.worker;

import com.htht.multisource.application.port.out.ProcessingPlanner;
import com.htht.multisource.application.port.out.ProductInspector;
import com.htht.multisource.application.port.out.TaskDispatcher;
import com.htht.multisource.application.port.out.model.DispatchReceipt;
import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.InspectionResponse;
import com.htht.multisource.application.port.out.model.PlanRequest;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.ProcessingPlan;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class LocalProcessWorkerGateway implements ProductInspector, ProcessingPlanner, TaskDispatcher {

    private final WorkerContractCodec codec;
    private final WorkerResultTranslator translator;
    private final WorkerProcessLauncher launcher;
    private final LocalWorkerAssetManifestWriter manifestWriter;
    private final Supplier<String> requestIdSupplier;
    private final Map<ExecutionId, String> runningRequests = new ConcurrentHashMap<>();

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

    @Override
    public ProcessingPlan buildPlan(PlanRequest request) {
        String requestId = nextRequestId(WorkerOperation.BUILD_PLAN);
        return translator.toPlan(invoke(requestId, WorkerOperation.BUILD_PLAN, codec.encodeBuildPlan(requestId, request),
            manifestWriter == null ? null : manifestWriter.write(requestId, request.readGrant(), request.candidate().files())));
    }

    @Override
    public DispatchReceipt dispatch(StageExecutionRequest request) {
        String requestId = nextRequestId(WorkerOperation.EXECUTE_STAGE);
        runningRequests.put(request.executionId(), requestId);
        try {
            return translator.toDispatchReceipt(invoke(
                requestId, WorkerOperation.EXECUTE_STAGE, codec.encodeStageExecution(requestId, request),
                manifestWriter == null ? null : manifestWriter.write(
                    requestId, request.executionGrant(), request.inputs(), request.outputUri())
            ));
        } finally {
            runningRequests.remove(request.executionId(), requestId);
        }
    }

    @Override
    public void cancel(ExecutionId executionId) {
        String requestId = runningRequests.remove(executionId);
        if (requestId != null) {
            launcher.cancel(requestId);
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
}
