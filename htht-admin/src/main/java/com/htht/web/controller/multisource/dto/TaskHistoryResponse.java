package com.htht.web.controller.multisource.dto;

import com.htht.multisource.application.usecase.model.TaskHistoryItem;

import java.util.List;

public record TaskHistoryResponse(List<TaskHistoryItem> items, long total, int page, int pageSize) {
    public TaskHistoryResponse {
        items = List.copyOf(items);
    }
}
