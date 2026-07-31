package com.htht.common.utils.excel;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelDataConvertException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractDataImportListener<T> extends AnalysisEventListener<T> {

    // 批量处理大小
    protected static final int BATCH_SIZE = 1000;
    // 每批数据
    protected final List<T> batchList = new ArrayList<>();
    // 导入结果
    private final ImportResult<T> importResult = new ImportResult<>();

    public ImportResult<T> getImportResult() {
        return importResult;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        // 增加总行数
        getImportResult().incrementTotalRows();
        
        try {
            // 业务校验（由子类实现）
            validateAndProcess(data, context);
            batchList.add(data);
            if (batchList.size() >= BATCH_SIZE) {
                processBatch();
            }
        } catch (ImportValidationException e) {
            // 处理校验失败的情况
            getImportResult().addRowError(
                context.readRowHolder().getRowIndex() + 1, 
                e.getMessage(), 
                e.getInvalidValue()
            );
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 处理最后一批数据
        if (!batchList.isEmpty()) {
            processBatch();
        }
        // 更新成功行数
        getImportResult().setSuccessRows();
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) {
        // 处理数据转换异常
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException ex = (ExcelDataConvertException) exception;
            getImportResult().addRowError(
                ex.getRowIndex() + 1,
                String.format("第%d列数据格式错误: %s", ex.getColumnIndex() + 1, ex.getMessage()),
                ex.getCellData()
            );
        } else if (!(exception instanceof ImportValidationException)) {
            // 其他异常处理（排除我们自己的校验异常）
            log.error("Excel解析异常: {}", exception.getMessage(), exception);
            getImportResult().addRowError(
                context.readRowHolder().getRowIndex() + 1,
                "解析错误: " + exception.getMessage(),
                null
            );
        }
    }
    
    /**​
     * 处理批数据（由子类实现）
     */
    protected abstract void processBatch();
    
    /**​
     * 校验并处理单行数据（由子类实现）
     */
    protected abstract void validateAndProcess(T data, AnalysisContext context) throws ImportValidationException;
}
