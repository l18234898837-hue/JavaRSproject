package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.StageId;

public interface StageIdGenerator {

    StageId nextId();
}
