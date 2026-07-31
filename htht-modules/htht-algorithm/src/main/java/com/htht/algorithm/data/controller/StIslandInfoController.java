package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.StIslandInfo;
import com.htht.algorithm.data.domain.vo.StIslandInfoQueryVO;
import com.htht.algorithm.data.service.StIslandInfoService;
import com.htht.common.annotation.Log;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.BusinessType;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * 存储群岛信息，含层级关系（parent_id和level） 前端控制器
 *
 *
 * @author MSFY
 * @since 2025-08-18
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stIslandInfo")
public class StIslandInfoController extends BaseController {

    private final StIslandInfoService stIslandInfoService;

    /**
     * 分页查询
     * @param queryVO
     * @return
     */
    @GetMapping("/page")
    public TableDataInfo<StIslandInfo> pageStIslandInfo(@ParameterObject StIslandInfoQueryVO queryVO) {
        return stIslandInfoService.pageStIslandInfo(queryVO);
    }

    @PostMapping
    @Log(title = "岛礁信息", businessType = BusinessType.INSERT)
    public R<Void> add(@RequestBody StIslandInfo stIslandInfo){
        StIslandInfo parentEntity = this.stIslandInfoService.getById(stIslandInfo.getParentId());
        if(parentEntity == null) {
            throw new IllegalArgumentException("所选择的父类岛礁不存在或已删除，请重新选择");
        }
        int level = parentEntity.getLevel();
        level ++;
        stIslandInfo.setLevel(level);
        return stIslandInfoService.save(stIslandInfo)?R.ok():R.fail();
    }

    @PutMapping
    @Log(title = "岛礁信息", businessType = BusinessType.UPDATE)
    public R<Void> update(@RequestBody StIslandInfo stIslandInfo){
        StIslandInfo parentEntity = this.stIslandInfoService.getById(stIslandInfo.getParentId());
        if(parentEntity == null) {
            throw new IllegalArgumentException("所选择的父类岛礁不存在或已删除，请重新选择");
        }
        int level = parentEntity.getLevel();
        level ++;
        stIslandInfo.setLevel(level);
        return stIslandInfoService.updateById(stIslandInfo)?R.ok():R.fail();
    }

    @DeleteMapping("/{id}")
    @Log(title = "岛礁信息", businessType = BusinessType.DELETE)
    public R<Void> delete(@PathVariable Long id){
        return stIslandInfoService.removeById(id)?R.ok():R.fail();
    }

    @GetMapping("/{id}")
    public R<StIslandInfo> findById(@PathVariable Long id){
        return R.ok(stIslandInfoService.getStIslandInfo(id));
    }
    @GetMapping()
    public R<List<StIslandInfo>> findAll(){
        return R.ok(stIslandInfoService.list());
    }

    @GetMapping("/tree")
    public R<List<StIslandInfo>> buildTree() {
        return R.ok(stIslandInfoService.buildTree());
    }

    /**
     * 导入群岛信息Excel文件
     *
     * @param file Excel文件
     * @return 导入结果
     */
    @PostMapping("/excel")
    public R<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            // 验证文件是否为空
            if (file.isEmpty()) {
                return R.fail("上传文件不能为空");
            }

            // 验证文件类型
            if (!file.getOriginalFilename().endsWith(".xlsx") &&
                !file.getOriginalFilename().endsWith(".xls")) {
                return R.fail("仅支持Excel文件格式");
            }

            // 解析Excel并导入数据
            List<StIslandInfo> islandList = parseExcel(file);

            // 批量保存数据
            boolean result = stIslandInfoService.addBatch(islandList);

            if (result) {
                return R.ok("importCount", islandList.size());
            } else {
                return R.fail("导入失败");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return R.fail("导入过程中发生错误：" + e.getMessage());
        }
    }

    /**
     * 解析Excel文件
     *
     * @param file Excel文件
     * @return 群岛信息列表
     * @throws IOException IO异常
     */
    private List<StIslandInfo> parseExcel(MultipartFile file) throws IOException {
        List<StIslandInfo> islandList = new ArrayList<>();

        // 使用Apache POI解析Excel
        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0); // 获取第一个工作表

            // 跳过标题行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                StIslandInfo island = new StIslandInfo();

                // 解析各列数据
                Cell archipelagoCell = row.getCell(1);
                if (archipelagoCell != null) {
                    island.setArchipelago(getCellValue(archipelagoCell));
                }

                Cell standardNameCell = row.getCell(2);
                if (standardNameCell != null) {
                    island.setStandardName(getCellValue(standardNameCell));
                }

                Cell chineseNameCell = row.getCell(3);
                if (chineseNameCell != null) {
                    island.setChineseName(getCellValue(chineseNameCell));
                }

                Cell latitudeCell = row.getCell(4);
                if (latitudeCell != null) {
                    island.setLatitude(getCellValue(latitudeCell));
                }

                Cell longitudeCell = row.getCell(5);
                if (longitudeCell != null) {
                    island.setLongitude(getCellValue(longitudeCell));
                }

                // 设置层级关系（需要根据业务逻辑处理）
                // 这里假设第一行是顶级节点，后续行根据名称判断父子关系
                islandList.add(island);
            }

            workbook.close();
        }

        return islandList;
    }

    /**
     * 获取单元格值
     *
     * @param cell 单元格
     * @return 单元格值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return cell.toString();
        }
    }

}
