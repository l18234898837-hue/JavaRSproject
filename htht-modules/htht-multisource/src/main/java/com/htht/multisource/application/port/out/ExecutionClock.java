package com.htht.multisource.application.port.out;

import java.time.Instant;

public interface ExecutionClock {

    Instant now();
}
