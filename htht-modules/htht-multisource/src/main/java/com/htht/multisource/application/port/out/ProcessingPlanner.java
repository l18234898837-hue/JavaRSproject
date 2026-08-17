package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.PlanRequest;
import com.htht.multisource.domain.model.ProcessingPlan;

public interface ProcessingPlanner {

    ProcessingPlan buildPlan(PlanRequest request);
}
