package com.htht.common.utils.excel;

import com.alibaba.excel.EasyExcel;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**​
 * EasyExcel数据导入工具类
 */
@Slf4j
public class ExcelImportUtil {

    /**​
     * 读取Excel数据（默认第一个sheet）
     *
     * @param inputStream   Excel输入流
     * @param dataModelClass 数据模型Class对象
     * @param consumer      每批数据处理回调
     * @param <T>           数据类型
     * @return ImportResult 导入结果
     */
    public static <T> ImportResult<T> readExcel(InputStream inputStream,
                                               Class<T> dataModelClass,
                                               Consumer<List<T>> consumer) {
        return readExcel(inputStream, dataModelClass, consumer, 1);
    }

    /**​
     * 读取Excel数据
     *
     * @param inputStream   Excel输入流
     * @param dataModelClass 数据模型Class对象
     * @param consumer      每批数据处理回调
     * @param sheetNo       sheet序号(从1开始)
     * @param <T>           数据类型
     * @return ImportResult 导入结果
     */
	public static <T> ImportResult<T> readExcel(InputStream inputStream, 
												Class<T> dataModelClass,
												Consumer<List<T>> consumer, 
												int sheetNo) {
		// 使用DefaultDataImportListener而不是AbstractDataImportListener
		DefaultDataImportListener<T> listener = new DefaultDataImportListener<>(consumer);
		return readExcel(inputStream, dataModelClass, listener, sheetNo);
	}

    /**​
    * 使用自定义监听器读取Excel数据
    */
   public static <T> ImportResult<T> readExcel(InputStream inputStream,
                                              Class<T> dataModelClass,
                                              AbstractDataImportListener<T> listener,
                                              int sheetNo) {
       try {
           EasyExcel.read(inputStream, dataModelClass, listener)
                   .sheet(sheetNo - 1)
                   .doRead();
       } catch (Exception e) {
           listener.getImportResult().fail("系统错误：" + e.getMessage());
       }
       return listener.getImportResult();
   }
    

}