package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ProcessingBatchIdGenerator;
import com.htht.multisource.application.port.out.ProcessingBatchRepository;
import com.htht.multisource.application.port.out.ProcessingPlanner;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.StageIdGenerator;
import com.htht.multisource.application.port.out.TaskIdGenerator;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.AssetMetadata;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.PlanRequest;
import com.htht.multisource.application.usecase.model.BatchCreateCommand;
import com.htht.multisource.application.usecase.model.BatchCreationResult;
import com.htht.multisource.application.usecase.service.CreateProcessingBatchService;
import com.htht.multisource.application.usecase.service.CreateProcessingTaskService;
import com.htht.multisource.application.usecase.service.TaskCreationCoordinator;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.htht.multisource.application.usecase.ApplicationUseCaseTestFixtures.candidate;
import static com.htht.multisource.application.usecase.ApplicationUseCaseTestFixtures.samplePlan;
import static com.htht.multisource.application.usecase.ApplicationUseCaseTestFixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class CreateProcessingTaskUseCaseTest {

    @Test
    void creationServicesDeclareRollbackTransactions() {
        assertThat(CreateProcessingTaskService.class.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(CreateProcessingBatchService.class.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void batchCommandCopiesSelectedCandidates() {
        ProductCandidate candidate = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
        List<ProductCandidate> selected = new ArrayList<>(List.of(candidate));

        BatchCreateCommand command = new BatchCreateCommand(
            "batch-01", subject(), selected, "{\"mode\":\"standard\"}", new StorageUri("rs://output/batch-01")
        );
        selected.clear();

        assertThat(command.candidates()).containsExactly(candidate);
        assertThatThrownBy(() -> command.candidates().add(candidate))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void batchCreationPersistsOneChildTaskAndPendingStagesPerSupportedCandidate() {
        ProductCandidate first = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
        ProductCandidate second = candidate("hy1c-granule-01", ProductSupportStatus.SUPPORTED);
        Fakes fakes = new Fakes(samplePlan());

        BatchCreationResult result = fakes.batchService().create(new BatchCreateCommand(
            "batch-01", subject(), List.of(first, second), "{\"mode\":\"standard\"}",
            new StorageUri("rs://output/batch-01")
        ));

        assertThat(result.batch().candidates()).containsExactly(first, second);
        assertThat(result.taskIds()).containsExactly(TaskId.of(101), TaskId.of(102));
        assertThat(fakes.batches.saved()).hasSize(1);
        assertThat(fakes.tasks.saved()).hasSize(2);
        assertThat(fakes.stages.saved()).hasSize(4);
        assertThat(fakes.tasks.saved()).allSatisfy(task ->
            assertThat(task.name()).isEqualTo("batch-01")
        );
    }

    @Test
    void directCreationPersistsSingleCandidateBatchAndTask() {
        ProductCandidate candidate = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
        Fakes fakes = new Fakes(samplePlan());

        TaskId taskId = fakes.taskService().create(new CreateProcessingTaskCommand(
            "task-01", subject(), candidate, "{\"mode\":\"standard\"}", new StorageUri("rs://output/task-01")
        ));

        assertThat(taskId).isEqualTo(TaskId.of(101));
        assertThat(fakes.batches.saved()).singleElement().satisfies(batch ->
            assertThat(batch.candidates()).containsExactly(candidate)
        );
        assertThat(fakes.tasks.saved()).singleElement().satisfies(task ->
            assertThat(task.batch().candidates()).containsExactly(candidate)
        );
        assertThat(fakes.tasks.saved()).singleElement().satisfies(task -> {
            assertThat(task.name()).isEqualTo("task-01");
            assertThat(task.parameterSnapshot()).isEqualTo("{\"mode\":\"standard\"}");
            assertThat(task.outputUri()).isEqualTo(new StorageUri("rs://output/task-01"));
        });
    }

    @Test
    void unsafeCandidateIsRejectedBeforePlannerIsCalled() {
        Fakes fakes = new Fakes(samplePlan());

        assertThatThrownBy(() -> fakes.taskService().create(new CreateProcessingTaskCommand(
            "unknown-01", subject(), candidate("unknown-01", ProductSupportStatus.UNKNOWN_PRODUCT),
            "{\"mode\":\"standard\"}", new StorageUri("rs://output/unknown-01")
        ))).isInstanceOf(IllegalArgumentException.class);

        assertThat(fakes.planner.requests()).isEmpty();
        assertThat(fakes.batches.saved()).isEmpty();
    }

    private static final class Fakes {
        private final InMemoryBatchRepository batches = new InMemoryBatchRepository();
        private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        private final InMemoryStageRepository stages = new InMemoryStageRepository();
        private final FixedPlanner planner;
        private final TaskCreationCoordinator coordinator;

        private Fakes(ProcessingPlan plan) {
            planner = new FixedPlanner(plan);
            coordinator = new TaskCreationCoordinator(
                tasks, stages, planner, new CreationAssetStorage(),
                new IncrementingTaskIdGenerator(), new IncrementingStageIdGenerator()
            );
        }

        private CreateProcessingTaskService taskService() {
            return new CreateProcessingTaskService(batches, new IncrementingBatchIdGenerator(), coordinator);
        }

        private CreateProcessingBatchService batchService() {
            return new CreateProcessingBatchService(batches, new IncrementingBatchIdGenerator(), coordinator);
        }
    }

    private static final class InMemoryBatchRepository implements ProcessingBatchRepository {
        private final List<ProcessingBatch> saved = new ArrayList<>();

        @Override
        public Optional<ProcessingBatch> findById(long batchId) {
            return saved.stream().filter(batch -> batch.id() == batchId).findFirst();
        }

        @Override
        public void save(ProcessingBatch batch) {
            saved.add(batch);
        }

        private List<ProcessingBatch> saved() {
            return List.copyOf(saved);
        }
    }

    private static final class InMemoryTaskRepository implements ProcessingTaskRepository {
        private final List<ProcessingTask> saved = new ArrayList<>();

        @Override
        public Optional<ProcessingTask> findById(TaskId taskId) {
            return saved.stream().filter(task -> task.id().equals(taskId)).findFirst();
        }

        @Override
        public void save(ProcessingTask task) {
            saved.add(task);
        }

        private List<ProcessingTask> saved() {
            return List.copyOf(saved);
        }
    }

    private static final class InMemoryStageRepository implements TaskStageRepository {
        private final List<TaskStage> saved = new ArrayList<>();

        @Override
        public Optional<TaskStage> findById(StageId stageId) {
            return saved.stream().filter(stage -> stage.id().equals(stageId)).findFirst();
        }

        @Override
        public List<TaskStage> findByTaskId(TaskId taskId) {
            return saved.stream().filter(stage -> stage.taskId().equals(taskId)).toList();
        }

        @Override
        public void save(TaskStage stage) {
            saved.add(stage);
        }

        private List<TaskStage> saved() {
            return List.copyOf(saved);
        }
    }

    private static final class FixedPlanner implements ProcessingPlanner {
        private final ProcessingPlan plan;
        private final List<PlanRequest> requests = new ArrayList<>();

        private FixedPlanner(ProcessingPlan plan) {
            this.plan = plan;
        }

        @Override
        public ProcessingPlan buildPlan(PlanRequest request) {
            requests.add(request);
            return plan;
        }

        private List<PlanRequest> requests() {
            return List.copyOf(requests);
        }
    }

    private static final class CreationAssetStorage implements AssetStorage {
        @Override
        public AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs) {
            return new AssetAccessGrant("read-" + subject.userId(), Instant.parse("2026-08-20T00:00:00Z"));
        }

        @Override
        public ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri) {
            throw new UnsupportedOperationException("Execution grants are outside creation tests.");
        }

        @Override
        public AssetMetadata resolve(AccessSubject subject, AssetReference reference) {
            throw new UnsupportedOperationException("Asset resolution is outside creation tests.");
        }
    }

    private static final class IncrementingTaskIdGenerator implements TaskIdGenerator {
        private long next = 101L;

        @Override
        public TaskId nextId() {
            return TaskId.of(next++);
        }
    }

    private static final class IncrementingBatchIdGenerator implements ProcessingBatchIdGenerator {
        private long next = 11L;

        @Override
        public long nextId() {
            return next++;
        }
    }

    private static final class IncrementingStageIdGenerator implements StageIdGenerator {
        private long next = 201L;

        @Override
        public StageId nextId() {
            return new StageId(next++);
        }
    }
}
