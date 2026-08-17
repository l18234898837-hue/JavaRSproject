package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.InspectionResult;

public interface ProductInspector {

    InspectionResult inspect(InspectionRequest request);
}
