package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.InspectionResponse;

public interface ProductInspector {

    InspectionResponse inspect(InspectionRequest request);
}
