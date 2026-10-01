package com.htht.multisource.application.usecase.model;

import java.util.List;

public record TaskHistoryPage(List<TaskHistoryItem> items, long total, int page, int pageSize) {
    public TaskHistoryPage { items = List.copyOf(items); }
}
