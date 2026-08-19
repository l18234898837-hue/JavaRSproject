package com.htht.multisource.infrastructure.persistence.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.htht.multisource.domain.model.AccessSubject;
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
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingTaskEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputAssetEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskProductEntity;

import java.util.ArrayList;
import java.util.List;

public final class TaskPersistenceMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            return entity;
        }).toList();
    }

    public List<TaskInputAssetEntity> toInputAssetEntities(ProcessingTask task) {
        return task.candidates().stream().flatMap(candidate -> candidate.files().stream()).map(asset -> {
            TaskInputAssetEntity entity = new TaskInputAssetEntity();
            entity.setTaskId(task.id().value());
            entity.setRole(asset.role());
            entity.setOrdinal(asset.ordinal());
            entity.setReferenceType(asset.reference().sourceType());
            entity.setReferenceValue(asset.reference().sourceId());
            entity.setStorageUriSnapshot(asset.storageUriSnapshot().value());
            entity.setChecksum(asset.checksum());
            entity.setRequired(asset.required());
            return entity;
        }).toList();
    }

    public ProductCandidate toCandidate(TaskProductEntity product, List<TaskInputAssetEntity> assets) {
        List<InputAsset> inputs = assets.stream().map(asset -> new InputAsset(
            asset.getRole(), asset.getOrdinal(), new AssetReference(asset.getReferenceType(), asset.getReferenceValue()),
            new StorageUri(asset.getStorageUriSnapshot()), asset.getChecksum(), asset.getRequired()
        )).toList();
        return new ProductCandidate(
            product.getPackageId(), new ProductProfileKey(product.getProductCode(), product.getProfileVersion()),
            ProductDataShape.valueOf(product.getDataShape()), ProductSupportStatus.valueOf(product.getSupportStatus()),
            inputs, product.getConfidence()
        );
    }

    private static String writePlan(ProcessingPlan plan) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("version", plan.version());
        ArrayNode stages = root.putArray("stages");
        for (ProcessingStage stage : plan.stages()) {
            ObjectNode node = stages.addObject();
            node.put("stageKey", stage.stageKey());
            node.put("stageCode", stage.stageCode());
            ArrayNode dependencies = node.putArray("dependsOnStageKeys");
            stage.dependsOnStageKeys().forEach(dependencies::add);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize processing plan.", exception);
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
                stages.add(new ProcessingStage(
                    stage.required("stageKey").asText(), stage.required("stageCode").asText(), dependencies
                ));
            }
            return new ProcessingPlan(root.required("version").asText(), stages);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid processing plan snapshot.", exception);
        }
    }
}
