package com.htht.multisource.application.usecase;
import com.htht.multisource.domain.model.*;
public interface TaskArtifactAccessGuard { void requireAccessible(TaskId taskId, AccessSubject subject); }
