package com.htht.multisource.application.port.out;

import com.htht.multisource.application.usecase.model.TaskDetailView;

/** Removes only derived storage owned by the supplied processing task. */
public interface TaskArtifactPurgePort {

    void purge(TaskDetailView detail);
}
