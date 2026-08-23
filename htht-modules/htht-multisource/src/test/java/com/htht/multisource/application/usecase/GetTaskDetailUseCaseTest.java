package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskStageView;
import com.htht.multisource.application.usecase.service.GetTaskDetailService;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.htht.multisource.application.usecase.ApplicationUseCaseTestFixtures.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class GetTaskDetailUseCaseTest {

    @Test
    void ownerCanReadTaskStagesAndArtifacts() {
        ProcessingTask task = task(TaskId.of(101), new AccessSubject(7L));
        GetTaskDetailService service = new GetTaskDetailService(
            new SingleTaskRepository(task),
            new SingleStageRepository(TaskId.of(101)),
            new SingleArtifactRepository(TaskId.of(101))
        );

        TaskDetailView detail = service.get(TaskId.of(101), new AccessSubject(7L));

        assertThat(detail.id()).isEqualTo(TaskId.of(101));
        assertThat(detail.stages()).extracting(TaskStageView::stageKey).containsExactly("READ");
        assertThat(detail.artifacts()).extracting(artifact -> artifact.type()).containsExactly("MANIFEST");
    }

    @Test
    void nonOwnerCannotReadTaskDetail() {
        ProcessingTask task = task(TaskId.of(101), new AccessSubject(7L));
        GetTaskDetailService service = new GetTaskDetailService(
            new SingleTaskRepository(task), new EmptyTaskStageRepository(), new EmptyArtifactRepository()
        );

        assertThatThrownBy(() -> service.get(TaskId.of(101), new AccessSubject(8L)))
            .isInstanceOf(SecurityException.class);
    }

    private static final class SingleTaskRepository implements ProcessingTaskRepository {
        private final ProcessingTask task;

        private SingleTaskRepository(ProcessingTask task) {
            this.task = task;
        }

        @Override
        public Optional<ProcessingTask> findById(TaskId taskId) {
            return task.id().equals(taskId) ? Optional.of(task) : Optional.empty();
        }

        @Override
        public void save(ProcessingTask task) {
            throw new UnsupportedOperationException("Detail tests are read-only.");
        }
    }

    private static final class SingleStageRepository implements TaskStageRepository {
        private final TaskId taskId;

        private SingleStageRepository(TaskId taskId) {
            this.taskId = taskId;
        }

        @Override
        public Optional<TaskStage> findById(StageId stageId) {
            return Optional.empty();
        }

        @Override
        public List<TaskStage> findByTaskId(TaskId taskId) {
            return this.taskId.equals(taskId)
                ? List.of(new TaskStage(new StageId(201), taskId, "READ", StageExecutionStatus.PENDING, 1))
                : List.of();
        }

        @Override
        public void save(TaskStage stage) {
            throw new UnsupportedOperationException("Detail tests are read-only.");
        }
    }

    private static final class SingleArtifactRepository implements ArtifactRepository {
        private final TaskId taskId;

        private SingleArtifactRepository(TaskId taskId) {
            this.taskId = taskId;
        }

        @Override
        public List<Artifact> findByTaskId(TaskId taskId) {
            return this.taskId.equals(taskId)
                ? List.of(new Artifact(taskId, new StageId(201), "MANIFEST",
                    new StorageUri("rs://output/101/manifest.json"), "sha256:abc"))
                : List.of();
        }

        @Override
        public void save(Artifact artifact) {
            throw new UnsupportedOperationException("Detail tests are read-only.");
        }
    }

    private static final class EmptyTaskStageRepository implements TaskStageRepository {
        @Override
        public Optional<TaskStage> findById(StageId stageId) {
            return Optional.empty();
        }

        @Override
        public List<TaskStage> findByTaskId(TaskId taskId) {
            return List.of();
        }

        @Override
        public void save(TaskStage stage) {
            throw new UnsupportedOperationException("Detail tests are read-only.");
        }
    }

    private static final class EmptyArtifactRepository implements ArtifactRepository {
        @Override
        public List<Artifact> findByTaskId(TaskId taskId) {
            return List.of();
        }

        @Override
        public void save(Artifact artifact) {
            throw new UnsupportedOperationException("Detail tests are read-only.");
        }
    }
}
