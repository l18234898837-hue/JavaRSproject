package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.TaskId;

import java.util.List;

public interface ArtifactRepository {

    List<Artifact> findByTaskId(TaskId taskId);

    void save(Artifact artifact);
}
