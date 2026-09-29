package com.htht.multisource.application.usecase.service;

import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.ProcessingPlan;

/**
 * Centralizes the version boundary between newly created v3 tasks and
 * historical plans. Historical plans remain readable, but are not executable.
 */
public final class PlanExecutionPolicy {

    private PlanExecutionPolicy() {
    }

    public static void requireDispatchable(ProcessingTask task) {
        if (!ProcessingPlan.CURRENT_VERSION.equals(task.plan().version())) {
            throw new IllegalStateException("LEGACY_PLAN_ARCHIVED: historical plans are read-only.");
        }
    }
}
