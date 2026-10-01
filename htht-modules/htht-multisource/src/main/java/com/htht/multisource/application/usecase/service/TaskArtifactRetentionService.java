package com.htht.multisource.application.usecase.service;
import com.htht.multisource.application.port.out.*;
import com.htht.multisource.application.usecase.*;
import com.htht.multisource.application.usecase.model.*;
import com.htht.multisource.domain.model.*;
import java.time.*; import java.util.*; import java.util.function.Supplier;
public final class TaskArtifactRetentionService implements TaskArtifactRetentionUseCase {
 private final ProcessingTaskRepository tasks; private final TaskArtifactRetentionRepository retentions; private final Supplier<Instant> clock;
 public TaskArtifactRetentionService(ProcessingTaskRepository tasks,TaskArtifactRetentionRepository retentions,Supplier<Instant> clock){this.tasks=Objects.requireNonNull(tasks);this.retentions=Objects.requireNonNull(retentions);this.clock=Objects.requireNonNull(clock);}
 public TaskArtifactRetention change(TaskId id,AccessSubject subject,ArtifactRetentionOperation op){var task=tasks.findById(id).filter(t->t.creator().equals(subject)).orElseThrow(()->new NoSuchElementException("Processing task not found."));var current=retentions.findOrCreate(task.id());var next=switch(op){case PIN->current.pin(subject,clock.get());case RECYCLE->current.recycle(subject,clock.get());case RESTORE->current.restore(subject,clock.get());};if(next==current)return current;if(!retentions.compareAndSet(current,next))throw new IllegalStateException("Artifact retention changed concurrently.");return next;}
}
