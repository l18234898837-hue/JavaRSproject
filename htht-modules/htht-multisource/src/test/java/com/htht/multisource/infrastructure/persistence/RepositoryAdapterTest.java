package com.htht.multisource.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageExecution;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.infrastructure.persistence.id.LongSupplierProcessingBatchIdGenerator;
import com.htht.multisource.infrastructure.persistence.id.LongSupplierStageIdGenerator;
import com.htht.multisource.infrastructure.persistence.id.LongSupplierTaskIdGenerator;
import com.htht.multisource.infrastructure.persistence.entity.ArtifactEntity;
import com.htht.multisource.infrastructure.persistence.entity.BatchProductEntity;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingBatchEntity;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingTaskEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputAssetEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskProductEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskStageEntity;
import com.htht.multisource.infrastructure.persistence.mapper.ArtifactMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.BatchProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingBatchMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingTaskMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskInputAssetMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskStageMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskEventMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskEventEntity;
import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class RepositoryAdapterTest {

    private final TaskPersistenceMapper persistenceMapper = new TaskPersistenceMapper();

    @Test
    void batchRepositoryPersistsAndRestoresCandidateSnapshots() {
        InMemoryProcessingBatchMapper batches = new InMemoryProcessingBatchMapper();
        InMemoryBatchProductMapper products = new InMemoryBatchProductMapper();
        MyBatisProcessingBatchRepository repository = new MyBatisProcessingBatchRepository(batches, products, persistenceMapper);
        var batch = RepositoryFixtures.batchWithTwoCandidates();

        repository.save(batch);

        assertThat(repository.findById(batch.id())).contains(batch);
        assertThat(products.values).hasSize(2);
    }

    @Test
    void taskRepositoryRestoresAssetsByCandidatePackageId() {
        InMemoryProcessingBatchMapper batches = new InMemoryProcessingBatchMapper();
        InMemoryBatchProductMapper batchProducts = new InMemoryBatchProductMapper();
        MyBatisProcessingBatchRepository batchRepository = new MyBatisProcessingBatchRepository(batches, batchProducts, persistenceMapper);
        var task = RepositoryFixtures.taskWithTwoCandidatesUsingMainZero();
        batchRepository.save(task.batch());
        InMemoryProcessingTaskMapper tasks = new InMemoryProcessingTaskMapper();
        InMemoryTaskProductMapper products = new InMemoryTaskProductMapper();
        InMemoryTaskInputAssetMapper assets = new InMemoryTaskInputAssetMapper();
        MyBatisProcessingTaskRepository repository = new MyBatisProcessingTaskRepository(
            tasks, products, assets, batchRepository, persistenceMapper
        );

        repository.save(task);

        assertThat(repository.findById(task.id())).contains(task);
        assertThat(assets.values).extracting(TaskInputAssetEntity::getPackageId)
            .containsExactly("fy3h-granule-01", "hy1c-granule-01");
    }

    @Test
    void taskRepositoryUpdatesLifecycleStatusWithoutInsertingTaskAgain() {
        InMemoryProcessingTaskMapper tasks = new InMemoryProcessingTaskMapper();
        InMemoryProcessingBatchMapper batches = new InMemoryProcessingBatchMapper();
        InMemoryBatchProductMapper batchProducts = new InMemoryBatchProductMapper();
        MyBatisProcessingBatchRepository batchRepository = new MyBatisProcessingBatchRepository(
            batches, batchProducts, persistenceMapper
        );
        var task = RepositoryFixtures.taskWithTwoCandidatesUsingMainZero();
        batchRepository.save(task.batch());
        MyBatisProcessingTaskRepository repository = new MyBatisProcessingTaskRepository(
            tasks, new InMemoryTaskProductMapper(), new InMemoryTaskInputAssetMapper(), batchRepository, persistenceMapper
        );
        repository.save(task);

        repository.updateLifecycleStatus(task.id(), com.htht.multisource.domain.model.TaskLifecycleStatus.SUCCEEDED);

        assertThat(tasks.values).hasSize(1);
        assertThat(tasks.values.get(0).getLifecycleStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void stageRepositoryPersistsAndFindsTaskStages() {
        InMemoryTaskStageMapper stages = new InMemoryTaskStageMapper();
        MyBatisTaskStageRepository repository = new MyBatisTaskStageRepository(stages, persistenceMapper);
        TaskStage stage = new TaskStage(new StageId(201L), TaskId.of(101L), "READ", StageExecutionStatus.PENDING, 1);

        repository.save(stage);

        assertThat(repository.findById(stage.id())).contains(stage);
        assertThat(repository.findByTaskId(stage.taskId())).containsExactly(stage);
    }

    @Test
    void stageRepositoryUpdatesAnExistingStageInsteadOfInsertingItAgain() {
        InMemoryTaskStageMapper stages = new InMemoryTaskStageMapper();
        MyBatisTaskStageRepository repository = new MyBatisTaskStageRepository(stages, persistenceMapper);
        TaskStage pending = new TaskStage(new StageId(201L), TaskId.of(101L), "READ", StageExecutionStatus.PENDING, 1);
        TaskStage running = pending.start(new ExecutionId("exec-01"), "worker-local",
            Instant.parse("2026-08-21T10:00:00Z"), Instant.parse("2026-08-21T10:05:00Z"));

        repository.save(pending);
        repository.save(running);

        assertThat(repository.findById(pending.id())).contains(running);
        assertThat(stages.values).hasSize(1);
    }

    @Test
    void stageRepositoryClearsExecutionContextWhenRetrying() {
        InMemoryTaskStageMapper stages = new InMemoryTaskStageMapper();
        MyBatisTaskStageRepository repository = new MyBatisTaskStageRepository(stages, persistenceMapper);
        Instant startedAt = Instant.parse("2026-08-21T10:00:00Z");
        TaskStage running = new TaskStage(new StageId(201L), TaskId.of(101L), "READ", StageExecutionStatus.PENDING, 1)
            .start(new ExecutionId("exec-01"), "worker-local", startedAt, startedAt.plusSeconds(300));
        TaskStage retrying = running.fail(new ExecutionId("exec-01"), 1, "WORKER_FAILED", "failed", startedAt.plusSeconds(1))
            .retry();

        repository.save(running);
        repository.save(retrying);

        assertThat(stages.values.get(0).getExecutionId()).isNull();
        assertThat(stages.values.get(0).getLeaseExpireAt()).isNull();
        assertThat(stages.values.get(0).getErrorCode()).isNull();
    }

    @Test
    void stageRepositoryReturnsTheAtomicallyClaimedStage() {
        InMemoryTaskStageMapper stages = new InMemoryTaskStageMapper();
        MyBatisTaskStageRepository repository = new MyBatisTaskStageRepository(stages, persistenceMapper);
        Instant startedAt = Instant.parse("2026-08-21T10:00:00Z");
        StageExecution execution = new StageExecution(new ExecutionId("exec-01"), "worker-local",
            startedAt.plusSeconds(300), null, null, startedAt, null);
        TaskStage claimed = new TaskStage(new StageId(201L), TaskId.of(101L), "READ",
            StageExecutionStatus.PENDING, 1).start(execution.executionId(), execution.workerId(),
            execution.startedAt(), execution.leaseExpiresAt());
        stages.claimResult = persistenceMapper.toStageEntity(claimed);

        var result = repository.claimNextRunnable(TaskId.of(101L), execution, startedAt);

        assertThat(result).hasValueSatisfying(claim -> assertThat(claim.stage()).isEqualTo(claimed));
        assertThat(stages.claimTaskId).isEqualTo(101L);
        assertThat(stages.claimExecutionId).isEqualTo("exec-01");
    }

    @Test
    void executionEventRepositoryTreatsDuplicateEventAsNoOp() {
        InMemoryTaskEventMapper events = new InMemoryTaskEventMapper();
        MyBatisExecutionEventRepository repository = new MyBatisExecutionEventRepository(events, persistenceMapper);
        ExecutionEvent event = new ExecutionEvent(TaskId.of(101L), new StageId(201L), new ExecutionId("exec-01"),
            1, 1, ExecutionEvent.Type.SUCCEEDED, "{}", Instant.parse("2026-08-21T10:01:00Z"),
            ExecutionEvent.Acceptance.APPLIED);

        assertThat(repository.insertIfAbsent(event)).isTrue();
        assertThat(repository.insertIfAbsent(event)).isFalse();
        assertThat(events.values).hasSize(1);
    }

    @Test
    void artifactRepositoryPersistsAndFindsTaskArtifacts() {
        InMemoryArtifactMapper artifacts = new InMemoryArtifactMapper();
        MyBatisArtifactRepository repository = new MyBatisArtifactRepository(artifacts, persistenceMapper);
        Artifact artifact = RepositoryFixtures.artifact();

        repository.save(artifact);

        assertThat(repository.findByTaskId(artifact.taskId())).containsExactly(artifact);
    }

    @Test
    void idGeneratorsAdaptPositiveSuppliedValuesToDomainIds() {
        LongSupplier supplier = () -> 101L;

        assertThat(new LongSupplierProcessingBatchIdGenerator(supplier).nextId()).isEqualTo(101L);
        assertThat(new LongSupplierTaskIdGenerator(supplier).nextId()).isEqualTo(TaskId.of(101L));
        assertThat(new LongSupplierStageIdGenerator(supplier).nextId()).isEqualTo(new StageId(101L));
    }

    private abstract static class InMemoryBaseMapper<T> implements BaseMapper<T> {
        protected final List<T> values = new ArrayList<>();
        private final Function<T, Long> idExtractor;

        private InMemoryBaseMapper(Function<T, Long> idExtractor) { this.idExtractor = idExtractor; }
        @Override public int insert(T entity) { values.add(entity); return 1; }
        @Override public T selectById(Serializable id) { return values.stream().filter(value -> idExtractor.apply(value).equals(id)).findFirst().orElse(null); }
        @Override public int deleteById(Serializable id) { throw new UnsupportedOperationException(); }
        @Override public int deleteById(T entity) { throw new UnsupportedOperationException(); }
        @Override public int deleteByMap(Map<String, Object> columnMap) { throw new UnsupportedOperationException(); }
        @Override public int delete(Wrapper<T> queryWrapper) { throw new UnsupportedOperationException(); }
        @Override public int deleteBatchIds(Collection<?> idList) { throw new UnsupportedOperationException(); }
        @Override public int updateById(T entity) {
            Long entityId = idExtractor.apply(entity);
            for (int index = 0; index < values.size(); index++) {
                if (idExtractor.apply(values.get(index)).equals(entityId)) {
                    values.set(index, entity);
                    return 1;
                }
            }
            return 0;
        }
        @Override public int update(T entity, Wrapper<T> updateWrapper) { throw new UnsupportedOperationException(); }
        @Override public List<T> selectBatchIds(Collection<? extends Serializable> idList) { throw new UnsupportedOperationException(); }
        @Override public List<T> selectByMap(Map<String, Object> columnMap) { throw new UnsupportedOperationException(); }
        @Override public Long selectCount(Wrapper<T> queryWrapper) { throw new UnsupportedOperationException(); }
        @Override public List<T> selectList(Wrapper<T> queryWrapper) { throw new UnsupportedOperationException(); }
        @Override public List<Map<String, Object>> selectMaps(Wrapper<T> queryWrapper) { throw new UnsupportedOperationException(); }
        @Override public List<Object> selectObjs(Wrapper<T> queryWrapper) { throw new UnsupportedOperationException(); }
        @Override public <P extends IPage<T>> P selectPage(P page, Wrapper<T> queryWrapper) { throw new UnsupportedOperationException(); }
        @Override public <P extends IPage<Map<String, Object>>> P selectMapsPage(P page, Wrapper<T> queryWrapper) { throw new UnsupportedOperationException(); }
    }

    private static final class InMemoryProcessingBatchMapper extends InMemoryBaseMapper<ProcessingBatchEntity> implements ProcessingBatchMyBatisMapper {
        private InMemoryProcessingBatchMapper() { super(ProcessingBatchEntity::getBatchId); }
    }
    private static final class InMemoryBatchProductMapper extends InMemoryBaseMapper<BatchProductEntity> implements BatchProductMyBatisMapper {
        private InMemoryBatchProductMapper() { super(BatchProductEntity::getBatchProductId); }
        @Override public List<BatchProductEntity> selectByBatchId(long batchId) { return values.stream().filter(value -> value.getBatchId() == batchId).toList(); }
    }
    private static final class InMemoryProcessingTaskMapper extends InMemoryBaseMapper<ProcessingTaskEntity> implements ProcessingTaskMyBatisMapper {
        private InMemoryProcessingTaskMapper() { super(ProcessingTaskEntity::getTaskId); }
        @Override public int updateLifecycleStatus(long taskId, String status) {
            ProcessingTaskEntity entity = selectById(taskId);
            if (entity == null) {
                return 0;
            }
            entity.setLifecycleStatus(status);
            return 1;
        }
    }
    private static final class InMemoryTaskProductMapper extends InMemoryBaseMapper<TaskProductEntity> implements TaskProductMyBatisMapper {
        private InMemoryTaskProductMapper() { super(TaskProductEntity::getTaskProductId); }
        @Override public List<TaskProductEntity> selectByTaskId(long taskId) { return values.stream().filter(value -> value.getTaskId() == taskId).toList(); }
    }
    private static final class InMemoryTaskInputAssetMapper extends InMemoryBaseMapper<TaskInputAssetEntity> implements TaskInputAssetMyBatisMapper {
        private InMemoryTaskInputAssetMapper() { super(TaskInputAssetEntity::getInputAssetId); }
        @Override public List<TaskInputAssetEntity> selectByTaskId(long taskId) { return values.stream().filter(value -> value.getTaskId() == taskId).toList(); }
    }
    private static final class InMemoryTaskStageMapper extends InMemoryBaseMapper<TaskStageEntity> implements TaskStageMyBatisMapper {
        private TaskStageEntity claimResult;
        private long claimTaskId;
        private String claimExecutionId;
        private InMemoryTaskStageMapper() { super(TaskStageEntity::getStageId); }
        @Override public List<TaskStageEntity> selectByTaskId(long taskId) { return values.stream().filter(value -> value.getTaskId() == taskId).toList(); }
        @Override public int updateExecutionState(TaskStageEntity entity) { return updateById(entity); }
        @Override public TaskStageEntity claimNextRunnable(long taskId, String executionId, String workerId,
                                                          Instant leaseExpiresAt, Instant startedAt) {
            claimTaskId = taskId;
            claimExecutionId = executionId;
            return claimResult;
        }
    }
    private static final class InMemoryTaskEventMapper extends InMemoryBaseMapper<TaskEventEntity> implements TaskEventMyBatisMapper {
        private InMemoryTaskEventMapper() { super(TaskEventEntity::getEventId); }

        @Override public int insertIfAbsent(TaskEventEntity entity) {
            boolean duplicate = values.stream().anyMatch(value -> value.getStageId().equals(entity.getStageId())
                && value.getAttempt().equals(entity.getAttempt())
                && value.getEventSequence().equals(entity.getEventSequence()));
            if (duplicate) {
                return 0;
            }
            entity.setEventId((long) values.size() + 1);
            return insert(entity);
        }
    }
    private static final class InMemoryArtifactMapper extends InMemoryBaseMapper<ArtifactEntity> implements ArtifactMyBatisMapper {
        private InMemoryArtifactMapper() { super(ArtifactEntity::getArtifactId); }
        @Override public List<ArtifactEntity> selectByTaskId(long taskId) { return values.stream().filter(value -> value.getTaskId() == taskId).toList(); }
    }

    private static final class RepositoryFixtures {
        private static com.htht.multisource.domain.model.ProcessingBatch batchWithTwoCandidates() {
            return taskWithTwoCandidatesUsingMainZero().batch();
        }

        private static com.htht.multisource.domain.model.ProcessingTask taskWithTwoCandidatesUsingMainZero() {
            var first = candidate("fy3h-granule-01", "FY-3H-MERSI", "rs://input/fy3h-granule-01.hdf");
            var second = candidate("hy1c-granule-01", "HY-1C-CZI", "rs://input/hy1c-granule-01.hdf");
            var batch = new com.htht.multisource.domain.model.ProcessingBatch(11L, "batch-01",
                com.htht.multisource.domain.model.ProcessingBatchStatus.CREATED, List.of(first, second));
            var plan = new com.htht.multisource.domain.model.ProcessingPlan("plan-v1", List.of(
                new com.htht.multisource.domain.model.ProcessingStage("READ", "READ_SOURCE", List.of())
            ));
            return new com.htht.multisource.domain.model.ProcessingTask(TaskId.of(101L), "task-01",
                new com.htht.multisource.domain.model.AccessSubject(7L),
                com.htht.multisource.domain.model.TaskLifecycleStatus.CREATED,
                com.htht.multisource.domain.model.QualityStatus.NOT_CHECKED, "{\"mode\":\"standard\"}",
                new com.htht.multisource.domain.model.StorageUri("rs://output/task-01"), plan, batch, List.of(first, second));
        }

        private static com.htht.multisource.domain.model.ProductCandidate candidate(String packageId, String productCode, String uri) {
            return new com.htht.multisource.domain.model.ProductCandidate(packageId,
                new com.htht.multisource.domain.model.ProductProfileKey(productCode, "v1"),
                com.htht.multisource.domain.model.ProductDataShape.SWATH_RASTER,
                com.htht.multisource.domain.model.ProductSupportStatus.SUPPORTED,
                List.of(new com.htht.multisource.domain.model.InputAsset("MAIN", 0,
                    new com.htht.multisource.domain.model.AssetReference("LOCAL_FILE", packageId + ".hdf"),
                    new com.htht.multisource.domain.model.StorageUri(uri), "sha256:" + packageId, true)), 0.91);
        }

        private static Artifact artifact() {
            return new Artifact(TaskId.of(101L), new StageId(201L), "OUTPUT",
                new com.htht.multisource.domain.model.StorageUri("rs://output/a.tif"), "sha256:a");
        }
    }
}
