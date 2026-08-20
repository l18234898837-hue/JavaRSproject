package com.htht.multisource.infrastructure.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.PlanRequest;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    }

    private static StageExecutionRequest stageRequest() {
        return new StageExecutionRequest(
            TaskId.of(101L), new StageId(201L), "CAL", 1, new ExecutionId("exec-01"),
            "1.0", "FY-3H-MERSI", "v1", "processor-1", "{\"mode\":\"standard\"}",
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
            new AssetAccessGrant("read-01", Instant.parse("2026-08-20T00:00:00Z"))
        );
    }

    private static ProductCandidate candidate() {
        return new ProductCandidate(
            "package-01", new ProductProfileKey("FY-3H-MERSI", "v1"),
            ProductDataShape.SWATH_RASTER, ProductSupportStatus.SUPPORTED,
            java.util.List.of(asset()), 0.95
        );
    }

    private static InputAsset asset() {
        return new InputAsset(
            "MAIN", 0, new AssetReference("LOCAL_FILE", "package-01.hdf"),
            new StorageUri("rs://input/package-01.hdf"), "sha256:package-01", true
        );
    }
}
