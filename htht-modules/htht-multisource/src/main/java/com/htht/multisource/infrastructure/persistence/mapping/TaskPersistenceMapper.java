package com.htht.multisource.infrastructure.persistence.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageExecution;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingTaskEntity;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingBatchEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputAssetEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskProductEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskStageEntity;
import com.htht.multisource.infrastructure.persistence.entity.ArtifactEntity;
import com.htht.multisource.infrastructure.persistence.entity.BatchProductEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskEventEntity;
import com.htht.multisource.application.port.out.model.ExecutionEvent;

import java.util.ArrayList;
import java.util.List;

public final class TaskPersistenceMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public ProcessingBatchEntity toBatchEntity(ProcessingBatch batch) {
        ProcessingBatchEntity entity = new ProcessingBatchEntity();
        entity.setBatchId(batch.id());
        entity.setName(batch.name());
        entity.setStatus(batch.status().name());
        return entity;
    }

    public ProcessingTaskEntity toEntity(ProcessingTask task) {
        ProcessingTaskEntity entity = new ProcessingTaskEntity();
        entity.setTaskId(task.id().value());
        entity.setBatchId(task.batch().id());
        entity.setTaskNo(Long.toString(task.id().value()));
        entity.setName(task.name());
        entity.setCreatorId(task.creator().userId());
        entity.setLifecycleStatus(task.lifecycleStatus().name());
        entity.setQualityStatus(task.qualityStatus().name());
        entity.setParameterSnapshot(task.parameterSnapshot());
        entity.setOutputUri(task.outputUri().value());
        entity.setPlanSnapshot(writePlan(task.plan()));
        return entity;
    }

    public ProcessingTask toTask(
        ProcessingTaskEntity entity,
        ProcessingBatch batch,
        List<ProductCandidate> candidates
    ) {
        return new ProcessingTask(
            TaskId.of(entity.getTaskId()), entity.getName(), new AccessSubject(entity.getCreatorId()),
            TaskLifecycleStatus.valueOf(entity.getLifecycleStatus()), QualityStatus.valueOf(entity.getQualityStatus()),
            entity.getParameterSnapshot(), new StorageUri(entity.getOutputUri()), readPlan(entity.getPlanSnapshot()), batch, candidates
        );
    }

    public List<TaskProductEntity> toProductEntities(ProcessingTask task) {
        return task.candidates().stream().map(candidate -> {
            TaskProductEntity entity = new TaskProductEntity();
            entity.setTaskId(task.id().value());
            entity.setPackageId(candidate.packageId());
            entity.setProductCode(candidate.profileKey().productCode());
            entity.setProfileVersion(candidate.profileKey().profileVersion());
            entity.setDataShape(candidate.dataShape().name());
            entity.setSupportStatus(candidate.supportStatus().name());
            entity.setConfidence(candidate.confidence());
            entity.setMetadataSnapshot(candidate.metadataSnapshot());
            return entity;
        }).toList();
    }

    public List<TaskInputAssetEntity> toInputAssetEntities(ProcessingTask task) {
        return task.candidates().stream().flatMap(candidate -> candidate.files().stream().map(asset -> {
            TaskInputAssetEntity entity = new TaskInputAssetEntity();
            entity.setTaskId(task.id().value());
            entity.setPackageId(candidate.packageId());
            entity.setRole(asset.role());
            entity.setOrdinal(asset.ordinal());
            entity.setReferenceType(asset.reference().sourceType());
            entity.setReferenceValue(asset.reference().sourceId());
            entity.setStorageUriSnapshot(asset.storageUriSnapshot().value());
            entity.setChecksum(asset.checksum());
            entity.setRequired(asset.required());
            return entity;
        })).toList();
    }

    public ProductCandidate toCandidate(TaskProductEntity product, List<TaskInputAssetEntity> assets) {
        List<InputAsset> inputs = assets.stream().map(asset -> new InputAsset(
            asset.getRole(), asset.getOrdinal(), new AssetReference(asset.getReferenceType(), asset.getReferenceValue()),
            new StorageUri(asset.getStorageUriSnapshot()), asset.getChecksum(), asset.getRequired()
        )).toList();
        return new ProductCandidate(
            product.getPackageId(), new ProductProfileKey(product.getProductCode(), product.getProfileVersion()),
            ProductDataShape.valueOf(product.getDataShape()), ProductSupportStatus.valueOf(product.getSupportStatus()),
            inputs, product.getMetadataSnapshot(), product.getConfidence()
        );
    }

    public BatchProductEntity toBatchProductEntity(long batchId, ProductCandidate candidate) {
        BatchProductEntity entity = new BatchProductEntity();
        entity.setBatchId(batchId);
        entity.setPackageId(candidate.packageId());
        entity.setProductCode(candidate.profileKey().productCode());
        entity.setProfileVersion(candidate.profileKey().profileVersion());
        entity.setDataShape(candidate.dataShape().name());
        entity.setSupportStatus(candidate.supportStatus().name());
        entity.setConfidence(candidate.confidence());
        entity.setInputAssetsSnapshot(writeAssets(candidate.files()));
        entity.setMetadataSnapshot(candidate.metadataSnapshot());
        return entity;
    }

    public ProductCandidate toBatchCandidate(BatchProductEntity entity) {
        return new ProductCandidate(entity.getPackageId(), new ProductProfileKey(entity.getProductCode(), entity.getProfileVersion()),
            ProductDataShape.valueOf(entity.getDataShape()), ProductSupportStatus.valueOf(entity.getSupportStatus()),
            readAssets(entity.getInputAssetsSnapshot()), entity.getMetadataSnapshot(), entity.getConfidence());
    }

    public TaskStageEntity toStageEntity(TaskStage stage) {
        TaskStageEntity entity = new TaskStageEntity();
        entity.setStageId(stage.id().value());
        entity.setTaskId(stage.taskId().value());
        entity.setStageKey(stage.stageKey());
        entity.setExecutionStatus(stage.status().name());
        entity.setAttempt(stage.attempt());
        if (stage.execution() != null) {
            entity.setExecutionId(stage.execution().executionId().value());
            entity.setWorkerId(stage.execution().workerId());
            entity.setLeaseExpireAt(stage.execution().leaseExpiresAt());
            entity.setHeartbeatAt(stage.execution().heartbeatAt());
            entity.setLogUri(stage.execution().logUri());
            entity.setErrorCode(stage.execution().errorCode());
            entity.setErrorMessage(stage.execution().errorMessage());
            entity.setStartedAt(stage.execution().startedAt());
            entity.setFinishedAt(stage.execution().finishedAt());
        }
        return entity;
    }

    public TaskStage toStage(TaskStageEntity entity) {
        StageExecution execution = entity.getExecutionId() == null ? null : new StageExecution(
            new ExecutionId(entity.getExecutionId()), entity.getWorkerId(), entity.getLeaseExpireAt(),
            entity.getHeartbeatAt(), entity.getLogUri(), entity.getErrorCode(), entity.getErrorMessage(),
            entity.getStartedAt(), entity.getFinishedAt()
        );
        return new TaskStage(new StageId(entity.getStageId()), TaskId.of(entity.getTaskId()), entity.getStageKey(),
            StageExecutionStatus.valueOf(entity.getExecutionStatus()), entity.getAttempt(), execution);
    }

    public TaskEventEntity toEventEntity(ExecutionEvent event) {
        TaskEventEntity entity = new TaskEventEntity();
        entity.setStageId(event.stageId().value());
        entity.setExecutionId(event.executionId().value());
        entity.setAttempt(event.attempt());
        entity.setEventSequence(event.eventSequence());
        entity.setEventType(event.type().name());
        entity.setAcceptanceStatus(event.acceptance().name());
        entity.setPayload(event.payload());
        entity.setOccurredAt(event.occurredAt());
        return entity;
    }

    public ArtifactEntity toArtifactEntity(Artifact artifact) {
        ArtifactEntity entity = new ArtifactEntity();
        entity.setTaskId(artifact.taskId().value());
        entity.setStageId(artifact.stageId().value());
        entity.setArtifactType(artifact.type());
        entity.setStorageUri(artifact.uri().value());
        entity.setStorageKey(artifact.storageKey());
        entity.setChecksum(artifact.checksum());
        entity.setSizeBytes(artifact.sizeBytes());
        return entity;
    }

    public Artifact toArtifact(ArtifactEntity entity) {
        return new Artifact(TaskId.of(entity.getTaskId()), new StageId(entity.getStageId()), entity.getArtifactType(),
            new StorageUri(entity.getStorageUri()), entity.getStorageKey(), entity.getChecksum(), entity.getSizeBytes());
    }

    private static String writePlan(ProcessingPlan plan) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("version", plan.version());
        root.put("processorVersion", plan.processorVersion());
        ArrayNode stages = root.putArray("stages");
        for (ProcessingStage stage : plan.stages()) {
            ObjectNode node = stages.addObject();
            node.put("stageKey", stage.stageKey());
            node.put("stageCode", stage.stageCode());
            ArrayNode dependencies = node.putArray("dependsOnStageKeys");
            stage.dependsOnStageKeys().forEach(dependencies::add);
            ArrayNode inputs = node.putArray("inputs");
            for (StageInput input : stage.inputs()) {
                ObjectNode inputNode = inputs.addObject();
                inputNode.put("key", input.key());
                inputNode.put("source", input.source().name());
                inputNode.put("artifactType", input.artifactType());
                inputNode.put("required", input.required());
            }
            ArrayNode outputs = node.putArray("outputs");
            for (StageOutput output : stage.outputs()) {
                ObjectNode outputNode = outputs.addObject();
                outputNode.put("artifactType", output.artifactType());
                outputNode.put("relativePath", output.relativePath());
                outputNode.put("retention", output.retention().name());
            }
            node.put("parameterSnapshot", stage.parameterSnapshot());
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize processing plan.", exception);
        }
    }

    private static String writeAssets(List<InputAsset> assets) {
        ArrayNode snapshot = OBJECT_MAPPER.createArrayNode();
        for (InputAsset asset : assets) {
            ObjectNode node = snapshot.addObject();
            node.put("role", asset.role());
            node.put("ordinal", asset.ordinal());
            node.put("referenceType", asset.reference().sourceType());
            node.put("referenceValue", asset.reference().sourceId());
            node.put("storageUri", asset.storageUriSnapshot().value());
            node.put("checksum", asset.checksum());
            node.put("required", asset.required());
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize input assets.", exception);
        }
    }

    private static List<InputAsset> readAssets(String snapshot) {
        try {
            List<InputAsset> assets = new ArrayList<>();
            for (JsonNode node : OBJECT_MAPPER.readTree(snapshot)) {
                assets.add(new InputAsset(node.required("role").asText(), node.required("ordinal").asInt(),
                    new AssetReference(node.required("referenceType").asText(), node.required("referenceValue").asText()),
                    new StorageUri(node.required("storageUri").asText()), node.required("checksum").asText(),
                    node.required("required").asBoolean()));
            }
            return assets;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid input asset snapshot.", exception);
        }
    }

    private static ProcessingPlan readPlan(String snapshot) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(snapshot);
            List<ProcessingStage> stages = new ArrayList<>();
            for (JsonNode stage : root.required("stages")) {
                List<String> dependencies = new ArrayList<>();
                for (JsonNode dependency : stage.required("dependsOnStageKeys")) {
                    dependencies.add(dependency.asText());
                }
                List<StageInput> inputs = new ArrayList<>();
                for (JsonNode input : stage.path("inputs")) {
                    inputs.add(new StageInput(input.required("key").asText(),
                        StageInput.Source.valueOf(input.required("source").asText()),
                        input.required("artifactType").asText(), input.required("required").asBoolean()));
                }
                List<StageOutput> outputs = new ArrayList<>();
                for (JsonNode output : stage.path("outputs")) {
                    outputs.add(new StageOutput(output.required("artifactType").asText(),
                        output.required("relativePath").asText(),
                        StageOutput.Retention.valueOf(output.required("retention").asText())));
                }
                stages.add(new ProcessingStage(
                    stage.required("stageKey").asText(), stage.required("stageCode").asText(), dependencies,
                    inputs, outputs, stage.path("parameterSnapshot").asText("{}")
                ));
            }
            String processorVersion = root.path("processorVersion").asText("unspecified");
            return new ProcessingPlan(root.required("version").asText(), processorVersion, stages);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid processing plan snapshot.", exception);
        }
    }
}
