package com.htht.business.utils;

import org.apache.poi.ss.usermodel.DateUtil;
import com.htht.business.domain.SdObserveData;
import com.htht.business.domain.SdWavelengthData;
import com.htht.common.exception.GlobalException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ExcelUtil {

    public static  List<SdWavelengthData> getWaveLengthData(Sheet sheet) {
        List<SdWavelengthData> dataList = new ArrayList<>();
        List<String> headers = readHeaders(sheet);
        List<String> firstColumn = readFirstColumnData(sheet);
        if (headers.isEmpty()) {
            throw new GlobalException("工作表表头为空，无法解析数据");
        }
        if(firstColumn.isEmpty()){
            throw new GlobalException("工作表第一列数据为空，无法解析数据");
        }
        for(int rowNum=1;rowNum<=sheet.getLastRowNum();rowNum++){
            Row row = sheet.getRow(rowNum);
            if(row==null){
                continue;
            }
            for(int cellNum=1;cellNum<headers.size();cellNum++){
                String cellValue = getCellValue(row.getCell(cellNum));
                if(!cellValue.isEmpty()){
                    dataList.add(new SdWavelengthData()
                        .setSdIdOrSite(headers.get(cellNum))
                        .setWavelength(firstColumn.get(rowNum))
                        .setData(getCellValue(row.getCell(cellNum))));
                }
            }

        }
        return dataList;
    }

    public static List<SdObserveData> getObserveData(Sheet sheet) {
        List<SdObserveData> dataList = new ArrayList<>();
        if (sheet == null) {
            return dataList;
        }
        List<String> headers = readHeaders(sheet);
        if (headers.isEmpty()) {
            throw new GlobalException("工作表表头为空，无法解析数据");
        }
        System.out.println("表头信息：" + headers); // 调试用

        // 2. 遍历数据行（从第二行开始，索引=1）
        int lastRowNum = sheet.getLastRowNum();
        for (int rowNum = 1; rowNum <= lastRowNum; rowNum++) {
            Row dataRow = sheet.getRow(rowNum);
            if (dataRow == null) {
                continue; // 跳过空行
            }

            // 3. 根据表头映射数据到对象
            SdObserveData data = new SdObserveData();
            for (int cellNum = 0; cellNum < headers.size(); cellNum++) {
                String header = headers.get(cellNum); // 表头名称
                Cell cell = dataRow.getCell(cellNum); // 对应单元格
                String cellValue = getCellValue(cell); // 单元格值

                // 根据表头名称设置对象字段（这里需要根据你的SdObserveData类字段名适配）
                switch (header) {
                    case "ID":
                        data.setSdId(cellValue);
                        break;
                    case "site":
                        data.setSite(cellValue);
                        break;
                    case "日期":
                        // 日期需要特殊处理（如果单元格是日期类型）
                        // 可使用DateUtil.isCellDateFormatted(cell)判断后转换
                        if(!cellValue.isEmpty()){
                            LocalDate formatterLocalDate = getCellLocalDate(cell);
                            if(formatterLocalDate!=null){
                                data.setDate(formatterLocalDate); // 假设setDate接收字符串，实际可能需要Date类型
                            }
                        }
                        break;
                    case "时间":
                        if(!cellValue.isEmpty()){
                            LocalTime formatterLocalTime = getCellLocalTime(cell);
                            if(formatterLocalTime!=null){
                                data.setTime(formatterLocalTime); // 假设setTime接收字符串，实际可能需要LocalTime类型
                            }

                        }
                        break;
                    case "水深(m)":
                        if (!cellValue.isEmpty()) {
                            data.setWaterDepth(new BigDecimal(cellValue));
                        }
                        break;
                    case "longtitude":
                        if (!cellValue.isEmpty()) {
                            data.setLongitude(new BigDecimal(cellValue));
                        }
                        break;
                    case "latitude":
                        if (!cellValue.isEmpty()) {
                            data.setLatitude(new BigDecimal(cellValue));
                        }
                        break;
                    case "SPM(mg/L)":
                        if (!cellValue.isEmpty()) {
                            data.setSpm(new BigDecimal(cellValue));
                        }
                        break;
                    case "PIM(g/l)":
                        if (!cellValue.isEmpty()) {
                            data.setPim(new BigDecimal(cellValue));
                        }
                        break;
                    case "POM(g/l)":
                        if (!cellValue.isEmpty()) {
                            data.setPom(new BigDecimal(cellValue));
                        }
                        break;
                    case "PIM(%)":
                        if (!cellValue.isEmpty()) {
                            data.setPimPer(new BigDecimal(cellValue));
                        }
                        break;
                    case "POM（%）":
                        if (!cellValue.isEmpty()) {
                            data.setPomPer(new BigDecimal(cellValue));
                        }
                        break;
                    case "Chla(ug/L)":
                        if (!cellValue.isEmpty()) {
                            data.setChla(new BigDecimal(cellValue));
                        }
                        break;
                    case "SST":
                        if (!cellValue.isEmpty()) {
                            data.setSst(new BigDecimal(cellValue));
                        }
                        break;
                    case "salinity":
                        if (!cellValue.isEmpty()) {
                            data.setSalinity(new BigDecimal(cellValue));
                        }
                        break;
                    case "TSM":
                        if(!cellValue.isEmpty()){
                            data.setTsm(new BigDecimal(cellValue));
                        }
                    case "SSH":
                        if(!cellValue.isEmpty()){
                            data.setSsh(new BigDecimal(cellValue));
                        }
                    case "wind":
                        if(!cellValue.isEmpty()){
                            data.setWind(new BigDecimal(cellValue));
                        }
                    case "current":
                        if(!cellValue.isEmpty()){
                            data.setCurrent(new BigDecimal(cellValue));
                        }
                    case "ag（400)":
                        if (!cellValue.isEmpty()) {
                            data.setAg(new BigDecimal(cellValue));
                        }
                        break;
                    case "s（1/nm)":
                            if (!cellValue.isEmpty()) {
                                data.setS(new BigDecimal(cellValue));
                            }
                    case "CDOM":
                        if (!cellValue.isEmpty()) {
                            data.setCdom(cellValue);
                        }
                    case "光谱仪":
                        if (!cellValue.isEmpty()) {
                            data.setDeviceModel(cellValue);
                        }
                    default:
                        // 忽略未知表头
                        break;
                }
            }
            dataList.add(data);
        }
        return dataList;
    }



    public static void startReadObserveExcel(MultipartFile file){

        try(Workbook workbook = WorkbookFactory.create(file.getInputStream())){
            for(int sheetIndex = 0;sheetIndex<workbook.getNumberOfSheets();sheetIndex++){
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                if(sheetName.equals("Rrs")){
//                    Map<String, Map<String, String>> waveLengthData = getWaveLengthData(sheet);
                }else if(sheetName.equals("site")){
//                    List<SdObserveData> observeData = getObserveData(sheet);
                }
            }
        }catch  ( IOException e){
           throw new GlobalException(e.getMessage());
        }

    }

    public static void startReadObserveExcelTest(File file){
        try(Workbook workbook = WorkbookFactory.create(file)){
            for(int sheetIndex = 0;sheetIndex<workbook.getNumberOfSheets();sheetIndex++){
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                if(sheetName.equals("spectrum")){
                    List<SdWavelengthData> waveLengthData = getWaveLengthData(sheet);
                    System.out.println(waveLengthData.size());
                }else if(sheetName.equals("metadata")){
                    List<SdObserveData> observeData = getObserveData(sheet);
                    System.out.println(observeData);
                    System.out.println(observeData.size());
                }
            }
        }catch  ( IOException e){
            throw new GlobalException(e.getMessage());
        }

    }


    /**
     * 读取Excel表头（第一行数据）
     * @param sheet 工作表对象
     * @return 表头名称列表（按列顺序）
     */
    public static List<String> readHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        if (sheet == null) {
            return headers; // 工作表为空，返回空列表
        }

        // 获取第一行（表头行，索引从0开始）
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return headers; // 表头行为空，返回空列表
        }

        // 遍历表头行的所有单元格
        for (int cellNum = 0; cellNum <= headerRow.getLastCellNum(); cellNum++) {
            Cell cell = headerRow.getCell(cellNum);
            String headerValue = getCellValue(cell); // 统一处理单元格值为字符串
            if (headerValue != null && !headerValue.trim().isEmpty()) {
                headers.add(headerValue.trim());
            }
        }
        return headers;
    }

    /**
     * 读取Excel第一列的所有数据（按行顺序）
     * @param sheet 工作表对象
     * @return 第一列数据列表（每行第一列的值，按行顺序排列）
     */
    public static List<String> readFirstColumnData(Sheet sheet) {
        List<String> firstColumnData = new ArrayList<>();
        if (sheet == null) {
            return firstColumnData; // 工作表为空，返回空列表
        }

        // 获取最后一行的索引（行索引从0开始）
        int lastRowNum = sheet.getLastRowNum();
        // 遍历所有行（从第0行开始，包括表头行）
        for (int rowNum = 0; rowNum <= lastRowNum; rowNum++) {
            // 获取当前行对象
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                // 空行：第一列视为空值，添加空字符串
                firstColumnData.add("");
                continue;
            }

            // 获取当前行的第一列单元格（列索引为0）
            Cell firstCell = row.getCell(0);
            // 转换单元格值为字符串（复用之前的getCellValue方法）
            String cellValue = getCellValue(firstCell);
            // 去除首尾空格后添加到列表（空值处理为""）
            firstColumnData.add(cellValue != null ? cellValue.trim() : "");
        }

        return firstColumnData;
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return ""; // 单元格为空，返回空字符串
        }

        // 根据单元格类型提取值
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                double numericValue = cell.getNumericCellValue();
                if (numericValue == (long) numericValue) {
                    // 是整数（如340.0 → 340）
                    return String.valueOf((long) numericValue);
                } else {
                    // 是小数（如340.5 → 保留原格式）
                    return String.valueOf(numericValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // 公式单元格直接取计算结果
                return cell.getCellFormula(); // 或用evaluator.evaluate(cell)获取计算值
            default:
                return "";
        }
    }

    /**
     * 从日期类型单元格提取LocalDate（简化版）
     * 假设传入的单元格一定是日期类型
     */
    public static LocalDate getCellLocalDate(Cell cell) {
        if (cell == null) {
            System.out.println("单元格为空");
            return null;
        }

        try {
            // 1. 将Excel单元格值转为Date（处理数值型日期）
            Date date = DateUtil.getJavaDate(cell.getNumericCellValue());

            // 2. 转换为LocalDate（使用系统默认时区）
            return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        } catch (Exception e) {
            System.out.println("日期转换失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 从时间类型单元格提取LocalTime（简化版）
     * 假设传入的单元格一定是时间类型
     */
    public static LocalTime getCellLocalTime(Cell cell) {
        if (cell == null) {
            System.out.println("单元格为空");
            return null;
        }

        try {
            // 1. 将Excel单元格值转为Date（处理数值型时间）
            Date date = DateUtil.getJavaDate(cell.getNumericCellValue());

            // 2. 转换为LocalTime（使用系统默认时区）
            return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalTime();
        } catch (Exception e) {
            System.out.println("时间转换失败：" + e.getMessage());
            return null;
        }
    }


    public static void main(String[] args) {
        startReadObserveExcelTest(new File("C:\\Users\\思大锤\\Desktop\\temp\\卫星项目观测数据表.xlsx"));
    }


    //一开始的测试工具类，里面有大部分内容，经供参考，不会使用
    public  void Test() {
        try {
            File file = new File("C:\\Users\\思大锤\\Desktop\\temp\\卫星项目观测数据表.xlsx");
            Workbook workbook = WorkbookFactory.create(file);

            // 遍历所有表
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                System.out.println("Sheet名称: " + sheet.getSheetName());

                // 读取表格数据
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING:
                                System.out.print(cell.getStringCellValue() + "\t");
                                break;
                            case NUMERIC:
//                                if (DateUtil.isCellDateFormatted(cell)) {
//                                    System.out.print(cell.getDateCellValue() + "\t");
//                                } else {
//                                    System.out.print(cell.getNumericCellValue() + "\t");
//                                }
                                break;
                            case BOOLEAN:
                                System.out.print(cell.getBooleanCellValue() + "\t");
                                break;
                            case BLANK:
                                System.out.print(" \t");
                                break;
                            default:
                                System.out.print("未知类型\t");
                        }
                    }
                    System.out.println();
                }

                // 读取图片（仅适用于XLSX格式）
                if (sheet instanceof XSSFSheet xssfSheet) {
                    XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
                    if (drawing != null) {
                        for (XSSFShape shape : drawing.getShapes()) {
                            if (shape instanceof XSSFPicture picture) {
                                XSSFClientAnchor anchor = picture.getPreferredSize();
                                int row1 = anchor.getRow1();
                                int col1 = anchor.getCol1();
                                System.out.println("在第 " + (row1 + 1) + " 行，第 " + (col1 + 1) + " 列找到图片");

                                // 提取图片数据并保存
                                byte[] pictureData = picture.getPictureData().getData();
                                String imageFormat = picture.getPictureData().suggestFileExtension();
                                String imageFileName = "image_" + sheetIndex + "_" + row1 + "_" + col1 + "." + imageFormat;
                                FileOutputStream fos = new FileOutputStream(imageFileName);
                                fos.write(pictureData);
                                fos.close();
                            }
                        }
                    }
                }
            }

            workbook.close();
        } catch (IOException  e) {
            e.printStackTrace();
        }
    }
}
