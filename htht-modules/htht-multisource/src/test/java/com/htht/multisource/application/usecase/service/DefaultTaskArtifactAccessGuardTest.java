package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.*;
import com.htht.multisource.application.usecase.service.DefaultTaskArtifactAccessGuard;
import com.htht.multisource.domain.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("dev")
class DefaultTaskArtifactAccessGuardTest {
 @Test void recycleBinArtifactsAreDeniedToOwnerWithRecoverableCode() {
  TaskId id=TaskId.of(8L); AccessSubject owner=new AccessSubject(7L);
  TaskArtifactRetention retention=TaskArtifactRetention.available(id).recycle(owner, Instant.parse("2026-09-30T08:00:00Z"));
  DefaultTaskArtifactAccessGuard guard=new DefaultTaskArtifactAccessGuard(tasks(id,owner), repo(retention));
  assertThatThrownBy(()->guard.requireAccessible(id,owner)).isInstanceOf(ArtifactAccessDeniedException.class).extracting("code").isEqualTo("ARTIFACT_RECYCLED");
 }
 private static ProcessingTaskRepository tasks(TaskId id,AccessSubject owner){var task=ApplicationUseCaseTestFixtures.task(id,owner);return new ProcessingTaskRepository(){public Optional<ProcessingTask> findById(TaskId ignored){return Optional.of(task);}public void save(ProcessingTask ignored){}};}
 private static TaskArtifactRetentionRepository repo(TaskArtifactRetention value){return new TaskArtifactRetentionRepository(){public Optional<TaskArtifactRetention> find(TaskId id){return Optional.of(value);}public TaskArtifactRetention findOrCreate(TaskId id){return value;}public boolean compareAndSet(TaskArtifactRetention a,TaskArtifactRetention b){return false;}};}
}
