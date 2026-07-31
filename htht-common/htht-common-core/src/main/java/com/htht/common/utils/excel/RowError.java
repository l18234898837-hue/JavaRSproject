package com.htht.common.utils.excel;

import lombok.AllArgsConstructor;
import lombok.Data;

/**​
 * 行级错误信息
 */
@Data
@AllArgsConstructor
public class RowError {
    /**​
     * Excel中的行号（从1开始）
     */
    private int rowIndex;
    /**​
     * 错误信息
     */
    private String errorMsg;
    /**​
     * 错误的值（可选）
     */
    private Object errorValue;
}

