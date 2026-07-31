package com.htht.common.utils.excel;

import java.util.ArrayList;
import java.util.List;


import lombok.Data;

@Data
public class ImportResult<T> {
    /**​
     * 是否导入成功
     */
    private boolean success = true;
    /**​
     * 总数据行数
     */
    private int totalRows;
    /**​
     * 成功数据行数
     */
    private int successRows;
    /**​
     * 失败数据行数
     */
    private int errorRows;
    /**​
     * 错误消息
     */
    private String errorMsg;
    /**​
     * 数据转换错误列表
     */
    private List<RowError> rowErrors = new ArrayList<>();
    /**​
     * 成功数据（按需设置）
     */
    private List<T> successData;
    /**​
     * 失败数据（按需设置）
     */
    private List<T> errorData;

    public void fail(String message) {
        this.success = false;
        this.errorMsg = message;
    }

    public void addRowError(int rowIndex, String message, Object invalidValue) {
        this.rowErrors.add(new RowError(rowIndex, message, invalidValue));
        this.errorRows++;
    }
    
    public void incrementTotalRows() {
        this.totalRows++;
    }
    
    public void incrementSuccessRows(int count) {
        this.successRows += count;
    }
    
    public void setSuccessRows() {
        // 成功行数 = 总行数 - 错误行数
        this.successRows = this.totalRows - this.errorRows;
    }
}