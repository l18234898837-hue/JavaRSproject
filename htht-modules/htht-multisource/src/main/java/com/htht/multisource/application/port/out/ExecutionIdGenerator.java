package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.ExecutionId;

public interface ExecutionIdGenerator {

    ExecutionId nextId();
}
