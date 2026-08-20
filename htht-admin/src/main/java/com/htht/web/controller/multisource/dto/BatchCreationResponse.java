package com.htht.web.controller.multisource.dto;

import java.util.List;

public record BatchCreationResponse(long batchId, List<Long> taskIds) {
}
