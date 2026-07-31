package com.htht.common.utils.excel;

import com.alibaba.excel.context.AnalysisContext;

import java.util.List;
import java.util.function.Consumer;

public class DefaultDataImportListener<T> extends AbstractDataImportListener<T> {

    private final Consumer<List<T>> consumer;

    public DefaultDataImportListener(Consumer<List<T>> consumer) {
        this.consumer = consumer;
    }

    @Override
    protected void processBatch() {
        try {
            consumer.accept(batchList);
            // 增加成功行数
            getImportResult().incrementSuccessRows(batchList.size());
        } catch (Exception e) {
            // 处理整个批次的异常
            for (T data : batchList) {
                getImportResult().addRowError(-1, "处理异常: " + e.getMessage(), data);
            }
        } finally {
            batchList.clear();
        }
    }

    @Override
    protected void validateAndProcess(T data, AnalysisContext context) throws ImportValidationException {
        // 默认实现不进行校验，直接使用数据
        // 子类可以覆盖此方法进行验证
    }
}