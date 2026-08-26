package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.application.port.out.ArtifactValidator;
import com.htht.multisource.application.port.out.TaskArtifactStore;
import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.application.usecase.service.ArtifactRegistrationService;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.domain.model.ExecutionId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ArtifactRegistrationServiceTest {

    private static final TaskId TASK_ID = TaskId.of(101L);
    private static final StageId STAGE_ID = new StageId(201L);
    private static final StorageUri OUTPUT_URI = new StorageUri("rs://output/7/task-01");

    @Test
    void validatesAndRegistersWorkerArtifactOnce() {
        RecordingValidator validator = new RecordingValidator();
        InMemoryArtifactRepository repository = new InMemoryArtifactRepository();
        ArtifactRegistrationService service = new ArtifactRegistrationService(validator, repository);
        ArtifactCandidate candidate = artifact("result.tif", "sha256:result", 42);

        service.register(TASK_ID, STAGE_ID, OUTPUT_URI, new ExecutionId("exec-01"), List.of(candidate));
        service.register(TASK_ID, STAGE_ID, OUTPUT_URI, new ExecutionId("exec-01"), List.of(candidate));

        assertThat(validator.candidates).containsExactly(candidate, candidate);
        assertThat(repository.artifacts).containsExactly(
            new Artifact(TASK_ID, STAGE_ID, "OUTPUT", candidate.uri(), candidate.checksum(), candidate.sizeBytes())
        );
    }

    @Test
    void rejectsInvalidArtifactBeforePersistingAnything() {
        InMemoryArtifactRepository repository = new InMemoryArtifactRepository();
        ArtifactRegistrationService service = new ArtifactRegistrationService(new RejectingValidator(), repository);

        assertThatThrownBy(() -> service.register(TASK_ID, STAGE_ID, OUTPUT_URI, new ExecutionId("exec-01"),
            List.of(artifact("bad.tif", "sha256:bad", 3))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Artifact checksum does not match.");
        assertThat(repository.artifacts).isEmpty();
    }

    @Test
    void rejectsAnArtifactThatIsNotDeclaredByTheStageBeforeMaterializing() {
        RecordingValidator validator = new RecordingValidator();
        InMemoryArtifactRepository repository = new InMemoryArtifactRepository();
        RecordingArtifactStore store = new RecordingArtifactStore();
        ArtifactRegistrationService service = new ArtifactRegistrationService(validator, repository, store);
        ProcessingStage declaredStage = new ProcessingStage("WRITE", "WRITE", List.of(), List.of(),
            List.of(new StageOutput("SUMMARY", "summary.json", StageOutput.Retention.FINAL)), "{}");
        TaskStage taskStage = new TaskStage(STAGE_ID, TASK_ID, "WRITE", StageExecutionStatus.RUNNING, 1,
            new com.htht.multisource.domain.model.StageExecution(new ExecutionId("exec-01"), "worker",
                java.time.Instant.now().plusSeconds(60), null, null, java.time.Instant.now(), null));

        assertThatThrownBy(() -> service.register(TASK_ID, taskStage, declaredStage, OUTPUT_URI,
            new ExecutionId("exec-01"), List.of(artifact("other.json", "sha256:other", 3))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("declared");

        assertThat(validator.candidates).isEmpty();
        assertThat(store.materialized).isEmpty();
        assertThat(repository.artifacts).isEmpty();
    }

    private static ArtifactCandidate artifact(String name, String checksum, long size) {
        return new ArtifactCandidate(new StorageUri(OUTPUT_URI.value() + "/" + name), checksum, "OUTPUT", size);
    }

    private static final class RecordingValidator implements ArtifactValidator {
        private final List<ArtifactCandidate> candidates = new ArrayList<>();

        @Override
        public void validate(TaskId taskId, StageId stageId, ExecutionId executionId, StorageUri outputUri,
                             ArtifactCandidate candidate) {
            candidates.add(candidate);
        }
    }

    private static final class RejectingValidator implements ArtifactValidator {
        @Override
        public void validate(TaskId taskId, StageId stageId, ExecutionId executionId, StorageUri outputUri,
                             ArtifactCandidate candidate) {
            throw new IllegalArgumentException("Artifact checksum does not match.");
        }
    }

    private static final class InMemoryArtifactRepository implements ArtifactRepository {
        private final List<Artifact> artifacts = new ArrayList<>();

        @Override
        public List<Artifact> findByTaskId(TaskId taskId) {
            return artifacts.stream().filter(artifact -> artifact.taskId().equals(taskId)).toList();
        }

        @Override
        public void save(Artifact artifact) {
            artifacts.add(artifact);
        }
    }

    private static final class RecordingArtifactStore implements TaskArtifactStore {
        private final List<Artifact> materialized = new ArrayList<>();

        @Override
        public Artifact materialize(TaskId taskId, StageId stageId, String stageKey, int attempt,
                                    ExecutionId executionId, StorageUri outputUri, ArtifactCandidate candidate,
                                    StageOutput output) {
            Artifact artifact = new Artifact(taskId, stageId, candidate.type(), candidate.uri(), "101/output/" +
                output.relativePath(), candidate.checksum(), candidate.sizeBytes());
            materialized.add(artifact);
            return artifact;
        }

        @Override
        public java.nio.file.Path resolveForDownload(Artifact artifact) {
            throw new UnsupportedOperationException();
        }
    }
}
