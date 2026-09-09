package com.htht.multisource.infrastructure.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.PlanRequest;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@Tag("dev")
class WorkerContractCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkerContractCodec codec = new WorkerContractCodec(objectMapper);

    @Test
    void encodesVersionedStageRequestWithRequiredFields() throws Exception {
        String json = codec.encodeStageExecution("req-01", stageRequest());
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(root.path("operation").asText()).isEqualTo("execute-stage");
        assertThat(root.path("requestId").asText()).isEqualTo("req-01");
        assertThat(root.path("payload").path("taskId").asLong()).isEqualTo(101L);
        assertThat(root.path("payload").path("stageId").asLong()).isEqualTo(201L);
        assertThat(root.path("payload").path("executionId").asText()).isEqualTo("exec-01");
        assertThat(root.path("payload").path("inputs")).hasSize(1);
        assertThat(root.path("payload").path("outputUri").asText()).isEqualTo("rs://output/task-101");
    }

    @Test
    void rejectsResponseWhoseOperationDoesNotMatchInvocation() {
        String response = """
            {"schemaVersion":"1.0","operation":"inspect","requestId":"req-01","payload":{}}
            """;

        assertThatThrownBy(() -> codec.decodeResponse("req-01", WorkerOperation.EXECUTE_STAGE, response))
            .isInstanceOf(WorkerProtocolException.class)
            .hasMessageContaining("operation");
    }

    @Test
    void encodesInspectRequestWithAssetGrant() throws Exception {
        JsonNode root = objectMapper.readTree(codec.encodeInspect("req-inspect", inspectRequest()));

        assertThat(root.path("operation").asText()).isEqualTo("inspect");
        assertThat(root.path("payload").path("inputs")).hasSize(1);
        assertThat(root.path("payload").path("readGrant").path("grantReference").asText())
            .isEqualTo("read-01");
    }

    @Test
    void encodesBuildPlanWithSelectedCandidate() throws Exception {
        JsonNode root = objectMapper.readTree(codec.encodeBuildPlan("req-plan", planRequest()));

        assertThat(root.path("operation").asText()).isEqualTo("build-plan");
        assertThat(root.path("payload").path("taskId").asLong()).isEqualTo(101L);
        assertThat(root.path("payload").path("candidate").path("packageId").asText())
            .isEqualTo("package-01");
        assertThat(root.path("payload").path("requestedPlanVersion").asText())
            .isEqualTo("plan-v3");
    }

    @Test
    void decodesPlanV2StageContracts() {
        String response = """
            {"schemaVersion":"1.0","operation":"build-plan","requestId":"req-plan","payload":{
              "version":"plan-v2","stages":[
              {
                "stageKey":"DECODE","stageCode":"DECODE_SCALE_AND_NODATA","dependsOnStageKeys":[],
                "inputs":[{"key":"main","source":"RAW_INPUT","artifactType":"MAIN","required":true}],
                "outputs":[{"artifactType":"DECODED_CZI_RASTER","relativePath":"decoded/raster.npz","retention":"INTERMEDIATE"}],
                "parameterSnapshot":"{}"
              }, {
                "stageKey":"APPLY_L2_FLAGS","stageCode":"APPLY_L2_FLAGS","dependsOnStageKeys":["DECODE"],
                "inputs":[
                  {"key":"decoded","source":"UPSTREAM_ARTIFACT","artifactType":"DECODED_CZI_RASTER","required":true},
                  {"key":"main","source":"RAW_INPUT","artifactType":"MAIN","required":true}
                ],
                "outputs":[{"artifactType":"QUALITY_MASKED_CZI_RASTER","relativePath":"quality/masked.npz","retention":"INTERMEDIATE"}],
                "parameterSnapshot":"{\\"quality\\":{\\"policy\\":\\"default\\"}}"
              }]
            }}
            """;

        var plan = new WorkerResultTranslator(objectMapper).toPlan(
            codec.decodeResponse("req-plan", WorkerOperation.BUILD_PLAN, response)
        );

        assertThat(plan.stages().get(1).inputs()).containsExactly(
            new StageInput("decoded", StageInput.Source.UPSTREAM_ARTIFACT, "DECODED_CZI_RASTER", true),
            new StageInput("main", StageInput.Source.RAW_INPUT, "MAIN", true)
        );
        assertThat(plan.stages().get(1).outputs()).containsExactly(
            new StageOutput("QUALITY_MASKED_CZI_RASTER", "quality/masked.npz", StageOutput.Retention.INTERMEDIATE)
        );
        assertThat(plan.stages().get(1).parameterSnapshot()).contains("quality");
    }

    @Test
    void preservesWorkerDeclaredFailureDetails() {
        String response = """
            {"schemaVersion":"1.0","operation":"inspect","requestId":"req-01",
             "error":{"code":"UNSUPPORTED_PRODUCT","message":"No supported profile.","retryable":false}}
            """;

        Throwable thrown = catchThrowable(() -> codec.decodeResponse("req-01", WorkerOperation.INSPECT, response));
        assertThat(thrown).isInstanceOf(WorkerProtocolException.class)
            .hasMessageContaining("UNSUPPORTED_PRODUCT")
            .hasMessageContaining("retryable=false");
        WorkerProtocolException exception = (WorkerProtocolException) thrown;

        assertThat(exception.code()).isEqualTo("UNSUPPORTED_PRODUCT");
        assertThat(exception.retryable()).isFalse();
        assertThat(exception.isManifestExpired()).isFalse();
    }

    @Test
    void identifiesExpiredManifestError() {
        String response = """
            {"schemaVersion":"1.0","operation":"inspect","requestId":"req-01",
             "error":{"code":"INVALID_ASSET_MANIFEST","message":"manifest is expired","retryable":true}}
            """;

        Throwable thrown = catchThrowable(() -> codec.decodeResponse("req-01", WorkerOperation.INSPECT, response));
        assertThat(thrown).isInstanceOf(WorkerProtocolException.class);
        WorkerProtocolException exception = (WorkerProtocolException) thrown;

        assertThat(exception.isManifestExpired()).isTrue();
    }

    @Test
    void rejectsStageResultWithoutTerminalStatusAndArtifacts() {
        String response = """
            {"schemaVersion":"1.0","operation":"execute-stage","requestId":"req-01",
             "payload":{"workerId":"worker-local"}}
            """;

        assertThatThrownBy(() -> new WorkerResultTranslator(objectMapper).toDispatchReceipt(
            codec.decodeResponse("req-01", WorkerOperation.EXECUTE_STAGE, response)
        )).isInstanceOf(WorkerProtocolException.class)
            .hasMessageContaining("status");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"schemaVersion\":\"2.0\",\"operation\":\"inspect\",\"requestId\":\"req-01\",\"payload\":{}}",
        "{\"schemaVersion\":\"1.0\",\"operation\":\"inspect\",\"requestId\":\"other\",\"payload\":{}}",
        "not-json"
    })
    void rejectsInvalidOrMismatchedResponse(String response) {
        assertThatThrownBy(() -> codec.decodeResponse("req-01", WorkerOperation.INSPECT, response))
            .isInstanceOf(WorkerProtocolException.class);
    }

    private static StageExecutionRequest stageRequest() {
        return new StageExecutionRequest(
            TaskId.of(101L), new StageId(201L), "CAL", 1, new ExecutionId("exec-01"),
            "1.0", "FY-3H-MERSI", "v1", "processor-1", "{\"mode\":\"standard\"}",
            List.of(asset()), new StorageUri("rs://output/task-101"),
            new ExecutionGrant("write-01", Instant.parse("2026-08-20T00:00:00Z"))
        );
    }

    private static InspectionRequest inspectRequest() {
        return new InspectionRequest(
            new com.htht.multisource.domain.model.AccessSubject(7L),
            java.util.List.of(asset()),
            new AssetAccessGrant("read-01", Instant.parse("2026-08-20T00:00:00Z"))
        );
    }

    private static PlanRequest planRequest() {
        return new PlanRequest(
            TaskId.of(101L), candidate(), "{\"mode\":\"standard\"}",
            "plan-v3",
            new AssetAccessGrant("read-01", Instant.parse("2026-08-20T00:00:00Z"))
        );
    }

    private static ProductCandidate candidate() {
        return new ProductCandidate(
            "package-01", new ProductProfileKey("FY-3H-MERSI", "v1"),
            ProductDataShape.SWATH_RASTER, ProductSupportStatus.SUPPORTED,
            List.of(asset()), "{}", 0.95
        );
    }

    private static InputAsset asset() {
        return new InputAsset(
            "MAIN", 0, new AssetReference("LOCAL_FILE", "package-01.hdf"),
            new StorageUri("rs://input/package-01.hdf"), "sha256:package-01", true
        );
    }
}
