package com.htht.multisource.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.DispatchReceipt;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.InspectionResponse;
import com.htht.multisource.application.port.out.model.PlanRequest;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalProcessWorkerGatewayTest {

    @Test
    void inspectionGatewayReturnsAllCandidatesFromVerifiedResponse() {
        LocalProcessWorkerGateway gateway = gatewayReturning("inspect", """
            {"rulePackageVersion":"rules-2026.08","candidates":[
              {"packageId":"package-01","profileKey":{"productCode":"FY-3H-MERSI","profileVersion":"v1"},"dataShape":"SWATH_RASTER","supportStatus":"SUPPORTED","files":[{"role":"MAIN","ordinal":0,"reference":{"sourceType":"LOCAL_FILE","sourceId":"package-01.hdf"},"storageUriSnapshot":{"value":"rs://input/package-01.hdf"},"checksum":"sha256:package-01","required":true}],"metadataSnapshot":{},"confidence":0.91},
              {"packageId":"package-02","profileKey":{"productCode":"FY-3H-MERSI","profileVersion":"v1"},"dataShape":"SWATH_RASTER","supportStatus":"CANDIDATE","files":[{"role":"MAIN","ordinal":0,"reference":{"sourceType":"LOCAL_FILE","sourceId":"package-02.hdf"},"storageUriSnapshot":{"value":"rs://input/package-02.hdf"},"checksum":"sha256:package-02","required":true}],"metadataSnapshot":{},"confidence":0.81}
            ]}
            """);

        InspectionResponse response = gateway.inspect(inspectRequest());

        assertThat(response.rulePackageVersion()).isEqualTo("rules-2026.08");
        assertThat(response.candidates()).extracting(ProductCandidate::packageId)
            .containsExactly("package-01", "package-02");
    }

    @Test
    void plannerGatewayRebuildsValidatedProcessingPlan() {
        LocalProcessWorkerGateway gateway = gatewayReturning("build-plan", """
            {"version":"plan-v1","stages":[
              {"stageKey":"READ","stageCode":"READ_SOURCE","dependsOnStageKeys":[]},
              {"stageKey":"CAL","stageCode":"CALIBRATE","dependsOnStageKeys":["READ"]}
            ]}
            """);

        ProcessingPlan plan = gateway.buildPlan(planRequest());

        assertThat(plan.version()).isEqualTo("plan-v1");
        assertThat(plan.stages()).extracting(stage -> stage.stageKey()).containsExactly("READ", "CAL");
    }

    @Test
    void dispatcherGatewayReturnsStructuredTerminalResultWithoutPersistingTaskState() {
        LocalProcessWorkerGateway gateway = gatewayReturning("execute-stage", """
            {"workerId":"worker-local-01","status":"SUCCEEDED","artifacts":[]}
            """);

        DispatchReceipt receipt = gateway.dispatch(stageRequest());

        assertThat(receipt.workerId()).isEqualTo("worker-local-01");
        assertThat(receipt.succeeded()).isTrue();
        assertThat(receipt.artifacts()).isEmpty();
    }

    @Test
    void canceledReservationNeverLaunchesAQueuedWorker() {
        CancellableRecordingLauncher launcher = new CancellableRecordingLauncher();
        LocalProcessWorkerGateway gateway = new LocalProcessWorkerGateway(
            new WorkerContractCodec(new ObjectMapper()), new WorkerResultTranslator(new ObjectMapper()),
            launcher, () -> "req-reserved"
        );
        ExecutionId executionId = new ExecutionId("exec-01");

        gateway.reserve(executionId);
        gateway.cancel(executionId);

        assertThatThrownBy(() -> gateway.dispatch(stageRequest()))
            .isInstanceOf(CancellationException.class);
        assertThat(launcher.launchCalls).isZero();
        assertThat(launcher.reservedRequestId).isEqualTo("req-reserved");
        assertThat(launcher.canceledRequestId).isEqualTo("req-reserved");
        assertThat(launcher.releasedRequestId).isEqualTo("req-reserved");
    }

    @Test
    void gatewayCreatesAuthorizedManifestForEachWorkerOperation(@TempDir Path tempRoot) throws IOException {
        Path assetRoot = Files.createDirectories(tempRoot.resolve("assets"));
        Path input = Files.writeString(assetRoot.resolve("package-01.hdf"), "sample");
        Path workRoot = tempRoot.resolve("work");
        LocalWorkerAssetManifestWriter manifestWriter = new LocalWorkerAssetManifestWriter(
            workRoot,
            assetRoot,
            (grantReference, storageUri) -> Map.of("rs://input/package-01.hdf", input)
                .get(storageUri.value()),
            Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), java.time.ZoneOffset.UTC)
        );
        RecordingLauncher launcher = new RecordingLauncher();
        LocalProcessWorkerGateway gateway = new LocalProcessWorkerGateway(
            new WorkerContractCodec(new ObjectMapper()),
            new WorkerResultTranslator(new ObjectMapper()),
            launcher,
            new AtomicInteger()::toString,
            manifestWriter
        );

        gateway.inspect(inspectRequest());
        gateway.buildPlan(planRequest());
        gateway.dispatch(stageRequest());

        assertThat(launcher.invocations()).hasSize(3)
            .allSatisfy(invocation -> {
                assertThat(invocation.manifestFile()).isRegularFile();
                String directoryKey = invocation.operation() == WorkerOperation.EXECUTE_STAGE
                    ? "exec-01"
                    : invocation.requestId();
                assertThat(invocation.manifestFile()).isEqualTo(
                    workRoot.resolve(directoryKey).resolve("asset-manifest.json"));
                assertThat(new ObjectMapper().readTree(Files.readString(invocation.manifestFile()))
                    .at("/inputs/0/path").asText()).isEqualTo(input.toRealPath().toString());
            });
        assertThat(launcher.invocations()).extracting(invocation -> invocation.operation())
            .containsExactly(WorkerOperation.INSPECT, WorkerOperation.BUILD_PLAN, WorkerOperation.EXECUTE_STAGE);
    }

    private static LocalProcessWorkerGateway gatewayReturning(String operation, String payload) {
        AtomicInteger sequence = new AtomicInteger();
        return new LocalProcessWorkerGateway(
            new WorkerContractCodec(new ObjectMapper()), new WorkerResultTranslator(new ObjectMapper()),
            new StaticResponseLauncher(operation, payload), () -> "req-" + sequence.incrementAndGet()
        );
    }

    private static InspectionRequest inspectRequest() {
        return new InspectionRequest(new AccessSubject(7L), List.of(asset("package-01")), grant());
    }

    private static PlanRequest planRequest() {
        return new PlanRequest(TaskId.of(101L), candidate(), "{\"mode\":\"standard\"}", grant());
    }

    private static StageExecutionRequest stageRequest() {
        return new StageExecutionRequest(TaskId.of(101L), new StageId(201L), "CAL", 1,
            new ExecutionId("exec-01"), "1.0", "FY-3H-MERSI", "v1", "processor-1",
            "{\"mode\":\"standard\"}", List.of(asset("package-01")), new StorageUri("rs://output/task-101"),
            new ExecutionGrant("write-01", Instant.parse("2026-08-20T00:00:00Z"), TaskId.of(101L),
                new StageId(201L), new ExecutionId("exec-01"), 1));
    }

    private static ProductCandidate candidate() {
        return new ProductCandidate("package-01", new ProductProfileKey("FY-3H-MERSI", "v1"),
            ProductDataShape.SWATH_RASTER, ProductSupportStatus.SUPPORTED, List.of(asset("package-01")), 0.91);
    }

    private static InputAsset asset(String packageId) {
        return new InputAsset("MAIN", 0, new AssetReference("LOCAL_FILE", packageId + ".hdf"),
            new StorageUri("rs://input/" + packageId + ".hdf"), "sha256:" + packageId, true);
    }

    private static AssetAccessGrant grant() {
        return new AssetAccessGrant("read-01", Instant.parse("2026-08-20T00:00:00Z"));
    }

    private record StaticResponseLauncher(String operation, String payload) implements WorkerProcessLauncher {
        @Override
        public WorkerInvocationResult launch(WorkerInvocation invocation) {
            String response = "{\"schemaVersion\":\"1.0\",\"operation\":\"" + operation
                + "\",\"requestId\":\"" + invocation.requestId() + "\",\"payload\":" + payload + "}";
            return new WorkerInvocationResult(invocation.requestId(), 0, response);
        }

        @Override
        public void cancel(String requestId) {
        }
    }

    private static final class RecordingLauncher implements WorkerProcessLauncher {
        private final java.util.ArrayList<WorkerInvocation> invocations = new java.util.ArrayList<>();

        @Override
        public WorkerInvocationResult launch(WorkerInvocation invocation) {
            invocations.add(invocation);
            String operation = switch (invocation.operation()) {
                case INSPECT -> "inspect";
                case BUILD_PLAN -> "build-plan";
                case EXECUTE_STAGE -> "execute-stage";
            };
            String payload = switch (invocation.operation()) {
                case INSPECT -> "{\"rulePackageVersion\":\"rules-2026.08\",\"candidates\":["
                    + "{\"packageId\":\"package-01\",\"profileKey\":{\"productCode\":\"FY-3H-MERSI\",\"profileVersion\":\"v1\"},"
                    + "\"dataShape\":\"SWATH_RASTER\",\"supportStatus\":\"SUPPORTED\",\"files\":["
                    + "{\"role\":\"MAIN\",\"ordinal\":0,\"reference\":{\"sourceType\":\"LOCAL_FILE\",\"sourceId\":\"package-01.hdf\"},"
                    + "\"storageUriSnapshot\":{\"value\":\"rs://input/package-01.hdf\"},\"checksum\":\"sha256:package-01\",\"required\":true}],"
                    + "\"metadataSnapshot\":{},\"confidence\":0.91}]}";
                case BUILD_PLAN -> "{\"version\":\"plan-v1\",\"stages\":["
                    + "{\"stageKey\":\"READ\",\"stageCode\":\"READ_SOURCE\",\"dependsOnStageKeys\":[]}] }";
                case EXECUTE_STAGE -> "{\"workerId\":\"worker-local-01\",\"status\":\"SUCCEEDED\",\"artifacts\":[]}";
            };
            String response = "{\"schemaVersion\":\"1.0\",\"operation\":\"" + operation
                + "\",\"requestId\":\"" + invocation.requestId() + "\",\"payload\":" + payload + "}";
            return new WorkerInvocationResult(invocation.requestId(), 0, response);
        }

        @Override
        public void cancel(String requestId) {
        }

        private List<WorkerInvocation> invocations() {
            return invocations;
        }
    }

    private static final class CancellableRecordingLauncher implements WorkerProcessLauncher {
        private int launchCalls;
        private String reservedRequestId;
        private String canceledRequestId;
        private String releasedRequestId;

        @Override
        public void reserve(String requestId) {
            reservedRequestId = requestId;
        }

        @Override
        public WorkerInvocationResult launch(WorkerInvocation invocation) {
            launchCalls++;
            throw new AssertionError("Canceled worker must not be launched.");
        }

        @Override
        public void cancel(String requestId) {
            canceledRequestId = requestId;
        }

        @Override
        public void release(String requestId) {
            releasedRequestId = requestId;
        }
    }
}
