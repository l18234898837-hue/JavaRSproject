package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.TaskId;

public interface TaskIdGenerator {

    TaskId nextId();
}
