package com.htht.multisource.application.usecase.service;

import com.htht.multisource.domain.model.ProcessingTask;

/**
 * Centralizes the version boundary between newly created v3 tasks and
 * historical plans. Historical plans remain readable, but are not executable.
 */
public final class PlanExecutionPolicy {

    private PlanExecutionPolicy() {
    }

    public static void requirePlanV3ForNewTask(String version) {
        if (!"plan-v3".equals(version)) {
            throw new IllegalArgumentException("Only plan-v3 may be requested for new tasks.");
        }
    }

    public static void requireDispatchable(ProcessingTask task) {
        if (!"plan-v3".equals(task.plan().version())) {
            throw new IllegalStateException("LEGACY_PLAN_ARCHIVED: historical plans are read-only.");
        }
    }
}
