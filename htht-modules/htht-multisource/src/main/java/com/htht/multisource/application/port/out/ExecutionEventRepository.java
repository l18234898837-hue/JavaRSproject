package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.ExecutionEvent;

public interface ExecutionEventRepository {

    boolean insertIfAbsent(ExecutionEvent event);
}
