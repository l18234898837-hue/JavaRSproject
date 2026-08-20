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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class LocalProcessWorkerGatewayTest {

    @Test
    void inspectionGatewayReturnsAllCandidatesFromVerifiedResponse() {
        LocalProcessWorkerGateway gateway = gatewayReturning("inspect", """
            {"rulePackageVersion":"rules-2026.08","candidates":[
              {"packageId":"package-01","profileKey":{"productCode":"FY-3H-MERSI","profileVersion":"v1"},"dataShape":"SWATH_RASTER","supportStatus":"SUPPORTED","files":[{"role":"MAIN","ordinal":0,"reference":{"sourceType":"LOCAL_FILE","sourceId":"package-01.hdf"},"storageUriSnapshot":{"value":"rs://input/package-01.hdf"},"checksum":"sha256:package-01","required":true}],"confidence":0.91},
              {"packageId":"package-02","profileKey":{"productCode":"FY-3H-MERSI","profileVersion":"v1"},"dataShape":"SWATH_RASTER","supportStatus":"CANDIDATE","files":[{"role":"MAIN","ordinal":0,"reference":{"sourceType":"LOCAL_FILE","sourceId":"package-02.hdf"},"storageUriSnapshot":{"value":"rs://input/package-02.hdf"},"checksum":"sha256:package-02","required":true}],"confidence":0.81}
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
    void dispatcherGatewayReturnsWorkerIdWithoutPersistingTaskState() {
        LocalProcessWorkerGateway gateway = gatewayReturning("execute-stage", "{\"workerId\":\"worker-local-01\"}");

        DispatchReceipt receipt = gateway.dispatch(stageRequest());

        assertThat(receipt.workerId()).isEqualTo("worker-local-01");
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
            "{\"mode\":\"standard\"}", new ExecutionGrant("write-01", Instant.parse("2026-08-20T00:00:00Z")));
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
}
