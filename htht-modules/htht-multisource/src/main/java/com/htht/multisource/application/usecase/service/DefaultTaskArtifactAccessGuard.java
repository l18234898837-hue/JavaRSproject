package com.htht.multisource.application.usecase.service;
import com.htht.multisource.application.port.out.*; import com.htht.multisource.application.usecase.*; import com.htht.multisource.domain.model.*; import java.util.*;
public final class DefaultTaskArtifactAccessGuard implements TaskArtifactAccessGuard {
 private final ProcessingTaskRepository tasks; private final TaskArtifactRetentionRepository retentions;
 public DefaultTaskArtifactAccessGuard(ProcessingTaskRepository tasks,TaskArtifactRetentionRepository retentions){this.tasks=Objects.requireNonNull(tasks);this.retentions=Objects.requireNonNull(retentions);}
 public void requireAccessible(TaskId id,AccessSubject subject){var task=tasks.findById(id).filter(t->t.creator().equals(subject)).orElseThrow(()->new NoSuchElementException("Processing task not found."));var status=retentions.find(task.id()).map(TaskArtifactRetention::status).orElse(TaskArtifactRetentionStatus.AVAILABLE);if(status==TaskArtifactRetentionStatus.AVAILABLE||status==TaskArtifactRetentionStatus.PINNED)return;throw new ArtifactAccessDeniedException(code(status),"Task artifacts are not accessible: "+status);}
 private static String code(TaskArtifactRetentionStatus s){return switch(s){case RECYCLE_BIN->"ARTIFACT_RECYCLED";case PURGING->"ARTIFACT_PURGING";case PURGE_FAILED->"ARTIFACT_PURGE_FAILED";case PURGED->"ARTIFACT_PURGED";case MISSING->"ARTIFACT_MISSING";default->throw new IllegalArgumentException("Accessible status: "+s);};}
}
